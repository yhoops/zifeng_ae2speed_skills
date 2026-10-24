package org.zifeng.ae2skills.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.compat.CpuAdapterRegistry;
import org.zifeng.ae2skills.crafting.InstantCrafting;
import org.zifeng.ae2skills.crafting.VirtualCpus;
import org.zifeng.ae2skills.TickBudget;

@GameTestHolder(Addon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2SkillGameTests {
    private static final UUID SKILL_OWNER = UUID.fromString("ced33421-1e43-4b42-80cb-ac613136cb87");
    private static final BlockPos CPU = new BlockPos(1, 1, 1);
    private static final BlockPos PROVIDER = new BlockPos(3, 1, 1);

    /**
     * 三个用例共用进程级的 {@link Ae2Effects} / {@link TickBudget} 静态状态，必须分批次串行执行：
     * 同一批次内的用例会互相清空对方的技能等级（{@code Ae2Effects.remove} 是全量重算），
     * 从而产生与被测代码无关的随机失败。批次之间由 GameTest 框架保证顺序执行。
     */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "zifeng_main")
    public static void realNetworkCraftingAndVirtualCpus(GameTestHelper helper) {
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        var fixture = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(fixture::awaitNetwork)
                .thenExecute(fixture::prepare)
                .thenExecute(fixture::checkHostSkills)
                .thenExecute(fixture::checkLegacyMigration)
                .thenExecute(fixture::checkQuotasPowerAndProcessing)
                .thenExecute(fixture::checkWrappedCraftingPattern)
                .thenExecute(fixture::checkSplitCapacityCancellationAndSave)
                .thenExecute(fixture::checkUnlimitedDispatch)
                .thenExecute(fixture::checkDisabledChildDrains)
                .thenExecute(fixture::startNativeLargeOrder)
                .thenWaitUntil(fixture::awaitNativeLargeOrder)
                .thenExecute(fixture::checkRemaindersAndFullStorage)
                .thenExecute(fixture::checkSameOutputIsolation)
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 1, 2), AEBlocks.MOLECULAR_ASSEMBLER.block()))
                .thenWaitUntil(fixture::checkAssemblerOverclock)
                .thenExecute(() -> {
                    if (Boolean.getBoolean("zifeng.ae2.testSaveFailure")) fixture.checkReadFailureStopsAndPreserves();
                    else fixture.checkBreakRefund();
                })
                .thenExecute(() -> Ae2Effects.remove(SKILL_OWNER))
                .thenSucceed();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final MemoryStorage memory = new MemoryStorage();
        CraftingCPUCluster physical;
        CraftingCPUCluster second;
        IGrid grid;
        CraftingService service;
        AECraftingPattern planks;
        AECraftingPattern sticks;
        AECraftingPattern cake;
        AEProcessingPattern processing;

        Fixture(GameTestHelper helper) { this.helper = helper; }
        void assertThat(boolean condition, String message) { helper.assertTrue(condition, message); }
        void awaitNetwork() {
            CraftingBlockEntity be = helper.getBlockEntity(CPU);
            physical = be.getCluster();
            assertThat(physical != null && physical.isActive(), "Physical CPU must form and become active");
            PatternProviderBlockEntity provider = helper.getBlockEntity(PROVIDER);
            assertThat(provider.getMainNode().isActive(), "Pattern provider must be active");
            grid = physical.getGrid();
            assertThat(grid == provider.getMainNode().getGrid(), "CPU and provider must share the real grid");
            service = (CraftingService) grid.getCraftingService();
        }
        AECraftingPattern crafting(int slot, Item... recipe) {
            ItemStack[] inputs = new ItemStack[9];
            Arrays.fill(inputs, ItemStack.EMPTY);
            for (int i = 0; i < recipe.length; i++) if (recipe[i] != null) inputs[i] = new ItemStack(recipe[i]);
            var input = CraftingInput.of(3, 3, Arrays.asList(inputs));
            var holder = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            var encoded = AEItems.CRAFTING_PATTERN.stack();
            AECraftingPattern.encode(encoded, holder, inputs, holder.value().assemble(input, helper.getLevel().registryAccess()), false, false);
            install(slot, encoded);
            return new AECraftingPattern(AEItemKey.of(encoded), helper.getLevel());
        }
        void install(int slot, ItemStack pattern) {
            PatternProviderBlockEntity provider = helper.getBlockEntity(PROVIDER);
            provider.getLogic().getPatternInv().setItemDirect(slot, pattern);
            provider.getLogic().updatePatterns();
        }
        void prepare() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(memory, 0));
            planks = crafting(0, Items.OAK_LOG);
            sticks = crafting(1, Items.OAK_PLANKS, null, null, Items.OAK_PLANKS);
            cake = crafting(2, Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET,
                    Items.SUGAR, Items.EGG, Items.SUGAR, Items.WHEAT, Items.WHEAT, Items.WHEAT);
            var encoded = AEItems.PROCESSING_PATTERN.stack();
            AEProcessingPattern.encode(encoded, List.of(stack(Items.COBBLESTONE, 1)), List.of(stack(Items.DIAMOND, 1)));
            install(3, encoded);
            processing = new AEProcessingPattern(AEItemKey.of(encoded));
            List<ICraftingCPU> cpus = service.getCpus().stream().toList();
            assertThat(cpus.size() == 2, "Level 1 must expose two real selectable CPUs");
            second = (CraftingCPUCluster) cpus.stream().filter(c -> c != physical).findFirst().orElseThrow();
            assertThat(second.craftingLogic != physical.craftingLogic, "Virtual CPUs must have independent native logic");
            for (int level = 2; level <= 4; level++) {
                Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, level);
                assertThat(service.getCpus().size() == (1 << level), "Each split level must expose its exact total slot count");
            }
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            service.getCpus();
        }
        void add(Item item, long amount) {
            grid.getStorageService().getInventory().insert(AEItemKey.of(item), amount, Actionable.MODULATE, IActionSource.empty());
        }
        long stored(Item item) { return memory.contents.get(AEItemKey.of(item)); }
        void clear() {
            for (var cpu : service.getCpus()) cpu.cancelJob();
            memory.contents.clear();
        }
        void tick(CraftingCPUCluster cpu, IEnergyService energy) {
            TickBudget.markTickStart();
            InstantCrafting.tick(cpu.craftingLogic, energy, service);
        }
        void submit(CraftingCPUCluster cpu, ICraftingPlan plan) {
            assertThat(service.submitJob(plan, null, cpu, true, IActionSource.empty()).successful(),
                    "Native standalone submission must succeed: busy=" + cpu.isBusy()
                            + " storage=" + cpu.getAvailableStorage() + " physical=" + (cpu == physical));
        }
        TestPlan plan(Item output, long count, long bytes, KeyCounter used, Map<IPatternDetails, Long> steps) {
            return new TestPlan(stack(output, count), bytes, false, false, used, new KeyCounter(), new KeyCounter(), steps);
        }
        TestPlan sticks(long count, long bytes) {
            return plan(Items.STICK, count, bytes, keys(Items.OAK_PLANKS, count / 2), Map.of(sticks, count / 4));
        }
        void checkQuotasPowerAndProcessing() {
            for (int level = 1; level <= 4; level++) {
                clear();
                Ae2Effects.enableMachineInfinites(SKILL_OWNER, level, 1);
                add(Items.OAK_PLANKS, 128);
                submit(physical, sticks(256, 128));
                double[] charged = {0};
                IEnergyService metered = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{IEnergyService.class}, (p, method, args) -> {
                            if (method.getName().equals("extractAEPower")) {
                                if (args[1] == Actionable.MODULATE) charged[0] += (double) args[0];
                                return args[0];
                            }
                            return method.invoke(grid.getEnergyService(), args);
                        });
                tick(physical, metered);
                long operations = 1L << level;
                assertThat(stored(Items.STICK) == operations * 4, "Level " + level + " must obey its exact per-CPU quota");
                assertThat(charged[0] == operations * 2, "CPU power must scale with every actual craft");
            }
            clear();
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 5, 1);
            add(Items.OAK_PLANKS, 32);
            submit(physical, sticks(64, 128));
            IEnergyService noPower = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IEnergyService.class}, (p, method, args) -> method.getName().equals("extractAEPower")
                            ? 0D : method.invoke(grid.getEnergyService(), args));
            tick(physical, noPower);
            assertThat(physical.isBusy() && stored(Items.STICK) == 0, "Unpowered crafting must wait");
            assertThat(physical.craftingLogic.getStored(AEItemKey.of(Items.OAK_PLANKS)) == 32, "Failed energy check must preserve inputs");
            clear();
            add(Items.COBBLESTONE, 1);
            submit(physical, plan(Items.DIAMOND, 1, 128, keys(Items.COBBLESTONE, 1), Map.of(processing, 1L)));
            tick(physical, grid.getEnergyService());
            assertThat(physical.isBusy() && stored(Items.DIAMOND) == 0, "Processing patterns must never fabricate declared outputs");
            clear();
        }
        void checkWrappedCraftingPattern() {
            clear();
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 1, 1);
            var wrapper = (appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern.class},
                    (proxy, method, args) -> method.invoke(sticks, args));
            var provider = new appeng.api.networking.crafting.ICraftingProvider() {
                public List<IPatternDetails> getAvailablePatterns() { return List.of(wrapper); }
                public boolean isBusy() { return false; }
                public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                    throw new AssertionError("Wrapped crafting pattern must use instant execution, not external dispatch");
                }
            };
            service.addGlobalCraftingProvider(provider);
            try {
                add(Items.OAK_PLANKS, 8);
                submit(physical, plan(Items.STICK, 16, 128, keys(Items.OAK_PLANKS, 8), Map.of(wrapper, 4L)));
                tick(physical, grid.getEnergyService());
                assertThat(stored(Items.STICK) == 8, "Third-party crafting wrapper obeys the two-batch quota");
                tick(physical, grid.getEnergyService());
                assertThat(stored(Items.STICK) == 16 && !physical.isBusy(), "Wrapped pattern completes without an assembler");
                assertThat(stored(Items.OAK_PLANKS) == 0, "Wrapped pattern does not duplicate ingredients");
            } finally {
                clear();
                service.removeGlobalCraftingProvider(provider);
            }
        }
        void prepareTranscend() {
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(memory, 0));
            var encoded = AEItems.PROCESSING_PATTERN.stack();
            AEProcessingPattern.encode(encoded, List.of(stack(Items.COBBLESTONE, 1)), List.of(stack(Items.DIAMOND, 1)));
            install(3, encoded);
            processing = new AEProcessingPattern(AEItemKey.of(encoded));
        }

        long receiverCobblestone() {
            if (!(helper.getBlockEntity(new BlockPos(4, 1, 1)) instanceof net.minecraft.world.Container container)) return -1;
            long total = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(Items.COBBLESTONE)) total += stack.getCount();
            }
            return total;
        }

        /** 第一步：6 级生效后开单，只允许 1 批真正推给机器。 */
        void transcendPushVerificationBatch() {
            clear();
            Ae2Effects.enableDivineGrace(SKILL_OWNER, 6);
            add(Items.COBBLESTONE, 100);
            submit(physical, plan(Items.DIAMOND, 100, 1000, keys(Items.COBBLESTONE, 100), Map.of(processing, 100L)));
            TickBudget.markTickStart();
            physical.craftingLogic.tickCraftingLogic(grid.getEnergyService(), service);
            long pushed = receiverCobblestone();
            assertThat(pushed == 1, "Verification must let exactly one batch reach the machine, pushed=" + pushed);
            assertThat(physical.craftingLogic.getWaitingFor(AEItemKey.of(Items.DIAMOND)) == 1,
                    "The verification batch must be registered as waiting for its product");
        }

        /** 第二步：机器做完这一批，产物按 AE2 真实路径回到网络（CPU 的 waitingFor 优先接走）。 */
        void transcendReturnProduct() {
            long accepted = grid.getStorageService().getInventory()
                    .insert(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE, IActionSource.empty());
            assertThat(accepted == 1, "The returned product must be consumed by the crafting job, accepted=" + accepted);
        }

        /** 第三步：取证成功之后，剩下 99 批应当一次性结清，机器不再参与。 */
        void transcendExpectInstant() {
            TickBudget.markTickStart();
            physical.craftingLogic.tickCraftingLogic(grid.getEnergyService(), service);
            assertThat(!physical.isBusy(), "A verified processing pattern must finish the whole order");
            assertThat(stored(Items.DIAMOND) == 100, "Instant settlement must deliver every batch, stored=" + stored(Items.DIAMOND));
            assertThat(receiverCobblestone() == 1, "Settled batches must never be sent to the machine again");
            clear();
            Ae2Effects.enableDivineGrace(SKILL_OWNER, 0);
        }

        void checkHostSkills() {
            var record = new org.zifeng.skilltree.data.PlayerSkillRecord(SKILL_OWNER);
            // 买到全部技能的满级共需 1,312,500 点（万机之神的 6 级单级就要 1,000,000），
            // 因此这里必须给到 2,000,000；给 1,000,000 会让 learnSkill 在第 6 级前中断。
            record.setSkillPoints(2_000_000);
            String[] ids = {"ae_assembler_speed", "ae_cpu_speed", "ae_assembler_infinite", "ae_cpu_infinite",
                    org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE};
            int[] caps = {4, 4, 5, 5, 6};
            // 万机之神的加护是非等比费用：1000/5000/10000/50000/100000/1000000，合计 1,166,000。
            long[] totals = {7500, 15000, 62000, 62000, 1_166_000L};
            for (int i = 0; i < ids.length; i++) {
                String id = ids[i];
                assertThat(org.zifeng.skilltree.skill.Skills.ALL_SKILLS.contains(id)
                        && org.zifeng.skilltree.skill.Skills.isGlobalSkill(id), "Addon skill must be registered as GLOBAL in the original host");
                for (int level = 0; level < caps[i]; level++) assertThat(record.learnSkill(id), "Host must purchase addon skill level");
                assertThat(!record.canLearn(id) && !record.learnSkill(id), "Host must enforce addon level cap");
                assertThat(record.totalSpentOf(id) == totals[i], "Host must account exact addon skill cost/refund total");
                record.setActiveLevel(id, 1);
            }
            record.setEnabled(ids[0], false);
            var restored = org.zifeng.skilltree.data.PlayerSkillRecord.load(record.save());
            for (int i = 0; i < ids.length; i++) {
                assertThat(restored.getLearnedPoints(ids[i]) == caps[i] && restored.getActiveLevel(ids[i]) == 1,
                        "Original host NBT must preserve learned and selected addon levels");
            }
            assertThat(!restored.isEnabled(ids[0]), "Original host must preserve addon toggle");
            double refund = restored.resetSkill(ids[2]);
            assertThat(refund == totals[2] * org.zifeng.skilltree.Config.RESET_REFUND_RATE.get(), "Host refund uses addon cost table");
            UUID other = UUID.randomUUID();
            Ae2Effects.update(SKILL_OWNER, 4, 3, 2, 5, 0);
            Ae2Effects.update(other, 1, 1, 1, 1, 0);
            assertThat(Ae2Effects.getCpuInfiniteLevel() == 5 && Ae2Effects.getAssemblerSpeedLevel() == 4, "Global effects use highest enabled level");
            Ae2Effects.remove(SKILL_OWNER);
            assertThat(Ae2Effects.getCpuInfiniteLevel() == 1, "Logout recomputes remaining global maximum");
            Ae2Effects.remove(other);
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
        }
        void checkUnlimitedDispatch() {
            clear();
            long[] received = {0};
            boolean[] busy = {false};
            var machine = new appeng.api.networking.crafting.ICraftingProvider() {
                public List<IPatternDetails> getAvailablePatterns() { return List.of(processing); }
                public boolean isBusy() { return busy[0]; }
                public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                    for (var input : inputs) received[0] += input.get(AEItemKey.of(Items.COBBLESTONE));
                    return true;
                }
            };
            service.addGlobalCraftingProvider(machine);
            try {
                Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 4);
                assertThat(physical.getCoProcessors() == 0, "Split levels 1-4 must retain physical co-processor count");
                Ae2Effects.enableMachineSpeeds(SKILL_OWNER, 0, 4);
                assertThat(physical.getCoProcessors() == 4, "Ordinary CPU overclock must remain effective below split level 5");
                Ae2Effects.enableMachineSpeeds(SKILL_OWNER, 0, 0);
                Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 5);
                add(Items.COBBLESTONE, 20_000);
                submit(physical, plan(Items.DIAMOND, 10_000, 128, keys(Items.COBBLESTONE, 10_000), Map.of(processing, 10_000L)));
                second = (CraftingCPUCluster) service.getCpus().stream().filter(c -> c != physical).findFirst().orElseThrow();
                submit(second, plan(Items.DIAMOND, 10_000, 128, keys(Items.COBBLESTONE, 10_000), Map.of(processing, 10_000L)));
                assertThat(physical.getCoProcessors() == Integer.MAX_VALUE - 1 && second.getCoProcessors() == Integer.MAX_VALUE - 1,
                        "Both physical and virtual CPUs must report unlimited co-processors");
                long[] charged = {0};
                IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IEnergyService.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("extractAEPower")) {
                                if (args[1] == Actionable.MODULATE) charged[0] += ((Double) args[0]).longValue();
                                return args[0];
                            }
                            return method.invoke(grid.getEnergyService(), args);
                        });
                busy[0] = true;
                TickBudget.markTickStart(); physical.craftingLogic.tickCraftingLogic(energy, service);
                assertThat(received[0] == 0, "Unlimited dispatch respects busy processing machines");
                busy[0] = false;
                IEnergyService noPower = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IEnergyService.class},
                        (proxy, method, args) -> method.getName().equals("extractAEPower") ? 0D : method.invoke(grid.getEnergyService(), args));
                TickBudget.markTickStart(); physical.craftingLogic.tickCraftingLogic(noPower, service);
                assertThat(received[0] == 0 && physical.craftingLogic.getStored(AEItemKey.of(Items.COBBLESTONE)) == 10_000,
                        "Unlimited dispatch without power must preserve every input");
                try {
                    var clock = TickBudget.class.getDeclaredField("start");
                    clock.setAccessible(true); clock.setLong(null, System.nanoTime() - 46_000_000L);
                    physical.craftingLogic.tickCraftingLogic(energy, service);
                    assertThat(received[0] == 0, "Unlimited dispatch must defer work when the server time budget is exhausted");
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                for (int tick = 0; tick < 8; tick++) {
                    TickBudget.markTickStart(); physical.craftingLogic.tickCraftingLogic(energy, service);
                    second.craftingLogic.tickCraftingLogic(energy, service);
                }
                assertThat(received[0] > 16, "Unlimited dispatch must exceed the native one-operation window without overflow");
                assertThat(charged[0] == received[0], "Unlimited dispatch must charge power for every accepted input");
                assertThat(physical.craftingLogic.getWaitingFor(AEItemKey.of(Items.DIAMOND)) > 0
                        && second.craftingLogic.getWaitingFor(AEItemKey.of(Items.DIAMOND)) > 0, "Each independent CPU must dispatch processing work");
                assertThat(stored(Items.DIAMOND) == 0 && physical.isBusy() && second.isBusy(), "Dispatch must wait for real machine outputs");
                clear();
            } finally {
                service.removeGlobalCraftingProvider(machine);
                Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            }
        }
        void checkLegacyMigration() {
            clear();
            CompoundTag fixture;
            try (var input = getClass().getResourceAsStream("/legacy-1.5.3.nbt")) {
                assertThat(input != null, "Legacy migration fixture must be present");
                fixture = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            assertThat(fixture.getString("SourceModVersion").equals("1.5.3"), "Fixture must come from the previous implementation");
            var record = org.zifeng.skilltree.data.PlayerSkillRecord.load(fixture.getCompound("Player"));
            // 1.5.3 存档只有当时存在的四个技能；万机之神的加护是本次新增，必须保持未学习状态。
            for (String id : new String[]{"ae_assembler_speed", "ae_cpu_speed", "ae_assembler_infinite", "ae_cpu_infinite"}) {
                assertThat(record.getLearnedPoints(id) == org.zifeng.ae2skills.SkillDefinitions.maxLevel(id)
                        && record.getActiveLevel(id) == 2, "Legacy learned and active levels must migrate");
            }
            assertThat(record.getLearnedPoints(org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE) == 0,
                    "A skill that did not exist in the legacy save must stay unlearned");
            assertThat(!record.isEnabled("ae_assembler_speed"), "Legacy disabled skill must stay disabled");
            var cpu = fixture.getCompound("Cpu");
            physical.readFromNBT(cpu, helper.getLevel().registryAccess());
            second = (CraftingCPUCluster) service.getCpus().stream().filter(c -> c != physical).findFirst().orElseThrow();
            assertThat(physical.isBusy() && second.isBusy(), "Both legacy tasks must resume independently");
            assertThat(physical.craftingLogic.getStored(AEItemKey.of(Items.OAK_PLANKS)) == 32
                    && second.craftingLogic.getStored(AEItemKey.of(Items.OAK_PLANKS)) == 32, "Legacy CPU inventories must migrate intact");
            assertThat(second.getAvailableStorage() == 48, "Legacy shared capacity reservations must migrate");
            var resaved = new CompoundTag();
            physical.writeToNBT(resaved, helper.getLevel().registryAccess());
            assertThat(resaved.getCompound("job").getCompound("link").getUUID("craftId")
                    .equals(cpu.getCompound("job").getCompound("link").getUUID("craftId")), "Original crafting link must retain its identity");
            var corrupt = cpu.copy();
            corrupt.getList("zifeng_virtual_cpus", 10).getCompound(0).getCompound("job").getList("tasks", 10).set(0, new CompoundTag());
            boolean rejected = false;
            try { org.zifeng.ae2skills.crafting.SaveCompatibility.validate(helper.getLevel(), corrupt, helper.getLevel().registryAccess()); }
            catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains("pattern"); }
            assertThat(rejected, "Unknown saved patterns must be rejected instead of silently dropped");
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 5, 1);
            tick(physical, grid.getEnergyService()); tick(second, grid.getEnergyService());
            assertThat(!physical.isBusy() && !second.isBusy() && stored(Items.STICK) == 128,
                    "Migrated independent jobs must finish with exact outputs and no duplicated ingredients");
            clear();
        }
        void checkSplitCapacityCancellationAndSave() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            add(Items.OAK_PLANKS, 64);
            submit(physical, sticks(64, 1500));
            assertThat(!service.submitJob(sticks(64, 600), null, second, true, IActionSource.empty()).successful(),
                    "Two slots must share 2048 bytes, not each receive 2048");
            submit(second, sticks(64, 500));
            assertThat(physical.isBusy() && second.isBusy(), "One physical CPU must really run two jobs");
            CompoundTag saved = new CompoundTag();
            physical.writeToNBT(saved, helper.getLevel().registryAccess());
            assertThat(saved.getList("zifeng_virtual_cpus", 10).size() == 1, "Child job must be saved");
            // Recreate the idle in-memory instances without refunding; NBT is their sole source of truth.
            physical.craftingLogic.getInventory().clear();
            second.craftingLogic.getInventory().clear();
            physical.readFromNBT(saved, helper.getLevel().registryAccess());
            second = VirtualCpus.state(physical).children.getFirst();
            assertThat(second.isBusy() && second.craftingLogic.getStored(AEItemKey.of(Items.OAK_PLANKS)) == 32,
                    "Reload must restore the independent child job and its ingredients");
            assertThat(second.getAvailableStorage() == 48, "Shared reservations must survive NBT round-trip");
            second.cancelJob();
            assertThat(physical.isBusy() && !second.isBusy(), "Cancelling a child must not cancel its sibling");
            assertThat(stored(Items.OAK_PLANKS) == 32, "Cancellation refunds only that job's remaining ingredients");
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 0);
            assertThat(physical.isBusy(), "Disabling split must not cancel an existing job");
            assertThat(!service.submitJob(sticks(4, 1), null, second, true, IActionSource.empty()).successful(),
                    "A stale menu reference must not reuse a retired slot");
            clear();
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 5);
            assertThat(physical.getAvailableStorage() == Long.MAX_VALUE, "Maximum storage must have no 1 TB ceiling");
            add(Items.OAK_PLANKS, 2);
            submit(physical, sticks(4, 2_000_000_000_000L));
            assertThat(service.getCpus().size() == 2, "Maximum level allocates another slot on demand");
            clear();
        }
        void checkDisabledChildDrains() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            second = (CraftingCPUCluster) service.getCpus().stream().filter(c -> c != physical).findFirst().orElseThrow();
            add(Items.OAK_PLANKS, 2);
            submit(second, sticks(4, 128));
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 5, 0);
            assertThat(service.getCpus().contains(second) && second.isBusy(), "Disabling split must preserve an active child");
            tick(second, grid.getEnergyService());
            assertThat(stored(Items.STICK) == 4, "The retired child's existing order must finish");
            assertThat(service.getCpus().size() == 1, "The child must retire only after it drains");
            clear();
        }
        void startNativeLargeOrder() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 5, 5);
            add(Items.OAK_LOG, 125_000);
            submit(physical, plan(Items.STICK, 1_000_000, 2_000_000_000_000L,
                    keys(Items.OAK_LOG, 125_000), Map.of(planks, 125_000L, sticks, 250_000L)));
            try {
                var clock = TickBudget.class.getDeclaredField("start");
                clock.setAccessible(true);
                clock.setLong(null, System.nanoTime() - 46_000_000L);
                InstantCrafting.tick(physical.craftingLogic, grid.getEnergyService(), service);
                assertThat(physical.isBusy() && stored(Items.STICK) == 0
                                && physical.craftingLogic.getStored(AEItemKey.of(Items.OAK_LOG)) == 125_000,
                        "An exhausted tick budget must defer the intact job to the next tick");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                TickBudget.markTickStart();
            }
        }
        void awaitNativeLargeOrder() {
            assertThat(!physical.isBusy(), "Large multistep order must complete through native server ticking");
            assertThat(stored(Items.STICK) == 1_000_000 && stored(Items.OAK_LOG) == 0 && stored(Items.OAK_PLANKS) == 0,
                    "Million-item batch must consume exact raw materials and finish its intermediate recipes");
        }
        void checkRemaindersAndFullStorage() {
            clear();
            add(Items.MILK_BUCKET, 3); add(Items.SUGAR, 2); add(Items.EGG, 1); add(Items.WHEAT, 3);
            var inputs = keys(Items.MILK_BUCKET, 3);
            inputs.add(AEItemKey.of(Items.SUGAR), 2); inputs.add(AEItemKey.of(Items.EGG), 1); inputs.add(AEItemKey.of(Items.WHEAT), 3);
            submit(physical, plan(Items.CAKE, 1, 128, inputs, Map.of(cake, 1L)));
            tick(physical, grid.getEnergyService());
            assertThat(stored(Items.CAKE) == 1 && stored(Items.BUCKET) == 3, "Cake must return every milk bucket");
            clear();
            add(Items.OAK_PLANKS, 2);
            submit(physical, sticks(4, 128));
            memory.accept = false;
            tick(physical, grid.getEnergyService());
            assertThat(physical.craftingLogic.getStored(AEItemKey.of(Items.STICK)) == 4, "Full storage must retain finished output in CPU inventory");
            memory.accept = true;
            physical.craftingLogic.storeItems();
            assertThat(stored(Items.STICK) == 4, "Retained output must flush when space returns");
            clear();
        }
        void checkSameOutputIsolation() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 5, 1);
            second = (CraftingCPUCluster) service.getCpus().stream().filter(c -> c != physical).findFirst().orElseThrow();
            add(Items.OAK_PLANKS, 4);
            submit(physical, sticks(4, 128));
            submit(second, sticks(4, 128));
            tick(physical, grid.getEnergyService());
            assertThat(second.isBusy(), "One instant final output must not complete the sibling's identical order");
            tick(second, grid.getEnergyService());
            assertThat(stored(Items.STICK) == 8 && stored(Items.OAK_PLANKS) == 0, "Both orders must consume their own inputs");
            clear();
        }
        void checkReadFailureStopsAndPreserves() {
            CompoundTag original;
            try (var input = getClass().getResourceAsStream("/legacy-1.5.3.nbt")) {
                original = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("Cpu");
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
            // A real legacy child job whose pattern can no longer be decoded.
            original.getList("zifeng_virtual_cpus", 10).getCompound(0).getCompound("job").getList("tasks", 10).set(0, new CompoundTag());
            var registries = helper.getLevel().registryAccess();
            var server = helper.getLevel().getServer();
            assertThat(server.isRunning(), "Server must be running before the invalid load");
            boolean rejected = false;
            try { physical.readFromNBT(original, registries); }
            catch (IllegalStateException expected) {
                rejected = expected.getMessage().contains("Cannot restore AE2 skill jobs")
                        && expected.getMessage().contains("pattern");
            }
            assertThat(rejected, "Actual CPU reader must report the incompatible task");
            assertThat(!server.isRunning(), "Failed restore must stop the real server");
            assertThat(original.equals(VirtualCpus.state(physical).failedRestore), "Failed restore must retain the complete original CPU tree");
            var resaved = new CompoundTag();
            physical.writeToNBT(resaved, registries);
            for (String key : original.getAllKeys()) {
                assertThat(original.get(key).equals(resaved.get(key)), "Shutdown serialization must preserve original NBT: " + key);
            }
            org.slf4j.LoggerFactory.getLogger(Addon.MOD_ID).info("ADDON SAVE FAILURE SMOKE PASSED: real server stopped; original CPU and child NBT preserved");
        }
        void checkBreakRefund() {
            Ae2Effects.enableMachineInfinites(SKILL_OWNER, 0, 1);
            add(Items.OAK_PLANKS, 4);
            submit(physical, sticks(4, 128));
            submit(second, sticks(4, 128));
            CraftingBlockEntity be = helper.getBlockEntity(CPU);
            be.breakCluster();
            assertThat(stored(Items.OAK_PLANKS) == 4, "Breaking a physical CPU refunds all child job ingredients exactly once");
        }
        void checkAssemblerOverclock() {
            appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler = helper.getBlockEntity(new BlockPos(2, 1, 2));
            assertThat(assembler.getMainNode().isActive(), "Assembler must connect to the powered network");
            try {
                var power = assembler.getClass().getDeclaredMethod("userPower", int.class, int.class, double.class);
                power.setAccessible(true);
                Ae2Effects.enableMachineSpeeds(SKILL_OWNER, 0, 0);
                assertThat((Integer) power.invoke(assembler, 1, 10, 1D) == 10, "Unboosted assembler must retain native progress");
                Ae2Effects.enableMachineSpeeds(SKILL_OWNER, 4, 0);
                assertThat((Integer) power.invoke(assembler, 1, 10, 1D) == 50, "Max assembler overclock must actually multiply powered progress by five");
                assertThat((Integer) power.invoke(assembler, 1, 50, 5D) == 100, "Assembler progress coefficient must keep its native per-call cap");
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            finally { Ae2Effects.enableMachineSpeeds(SKILL_OWNER, 0, 0); }
        }
    }
    /**
     * 「万机之神的加护」的服务端契约：倍率表、全局发布语义、激活门槛、成就数据。
     *
     * <p>这里刻意不搭真实的「供应器 + 机器 + 活跃任务」产线——那部分由
     * {@code DivineGrace.tick} 的运行时判定负责，训练式复现已由 {@link #realNetworkCraftingAndVirtualCpus}
     * 覆盖；本测试保证的是「没有活跃任务时绝不空转加速」这条最容易回归的边界。
     */
    /**
     * 「万机之神」6 级瞬成的端到端回归：真实样板供应器 + 真实合成 CPU。
     * <p>流程刻意复刻真人操作顺序：先让机器做 1 批（此时该样板被锁住），再把这一批的产物
     * 送回网络（AE2 的真实回传路径），然后下一次 tick 应当把剩下 99 批一次性结清。
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "zifeng_transcend")
    public static void divineTranscendSettlesVerifiedPattern(GameTestHelper helper) {
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        // 样板供应器要把材料推给「机器」：放一个箱子当接收方，就能观察真实推送行为。
        helper.setBlock(new BlockPos(4, 1, 1), net.minecraft.world.level.block.Blocks.CHEST);
        var fixture = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(fixture::awaitNetwork)
                .thenExecute(fixture::prepareTranscend)
                .thenExecute(fixture::transcendPushVerificationBatch)
                .thenIdle(2)
                .thenExecute(fixture::transcendReturnProduct)
                .thenIdle(2)
                .thenExecute(fixture::transcendExpectInstant)
                .thenExecute(() -> Ae2Effects.remove(SKILL_OWNER))
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "zifeng_divine")
    public static void divineGraceSkillContract(GameTestHelper helper) {
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.DIVINE_CATEGORY_INDEX == org.zifeng.ae2skills.SkillDefinitions.HOST_CATEGORY_COUNT,
                "The divine tab must be appended right after the host's 11 tabs");
        int[] multipliers = {2, 8, 16, 128, 1024, 1024};
        long[] costs = {1000L, 5000L, 10000L, 50000L, 100000L, 1000000L};
        for (int level = 1; level <= 6; level++) {
            helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.divineMultiplier(level) == multipliers[level - 1],
                    "Divine multiplier table must be 2/8/16/128/1024/1024 at level " + level);
            helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.cost(org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE, level - 1) == costs[level - 1],
                    "Divine cost table must be non-geometric at level " + level);
        }
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.maxLevel(org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE) == 6,
                "Divine skill must cap at six levels");
        // 第 6 级不是更高倍率，只是解锁瞬成；倍率与 5 级持平，不能悄悄变成 ×2048。
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.divineMultiplier(6) == 1024,
                "Level 6 must keep the level-5 multiplier; its effect is instant completion, not a bigger factor");
        // 5 级成就的门槛被钉成常量 5：上限提到 6 之后，已有存档必须还能拿到旧的 5 级成就。
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.ACH_MACHINE_LEVEL == 5,
                "The level-5 advancement threshold must stay pinned at 5 after the cap rises");
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.ACH_MACHINE.equals(
                        org.zifeng.ae2skills.SkillDefinitions.achievementPathFor(
                                org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE, 5)),
                "Level 5 must still resolve to the old advancement");
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.ACH_TRANSCEND.equals(
                        org.zifeng.ae2skills.SkillDefinitions.achievementPathFor(
                                org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE, 6)),
                "Level 6 must resolve to the new advancement");
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.achievementPathFor(
                        org.zifeng.ae2skills.SkillDefinitions.DIVINE_MACHINE, 4) == null,
                "Below the pinned threshold no advancement may be granted");

        // 全局发布语义（Ae2Effects 是进程级共享状态，因此串行放在主回归里验证，见 checkHostSkills）。
        // 这里只验证「没有活跃任务就绝不加速」这条边界：DivineGrace 的激活判定完全由自身负责。
        org.zifeng.ae2skills.divine.DivineGrace.reset();
        org.zifeng.ae2skills.divine.DivineGrace.tick(helper.getLevel().getServer());
        helper.assertTrue(org.zifeng.ae2skills.divine.DivineGrace.activeMultiplier() == 0,
                "Divine grace must stay idle while no crafting job is active");
        // 未激活时绝不留下「让路」标记，否则装配超频会被误伤（isAccelerated 以 activeMultiplier>0 为前提）。
        helper.assertTrue(!org.zifeng.ae2skills.divine.DivineGrace.isAccelerated(null),
                "An idle divine grace must never mark any machine as accelerated");

        // 6 级瞬成的记账入口在「没有任务、没有样板」时必须是纯空转：既不能抛，也不能留下
        // 「已上锁」的残留——否则该 CPU 上此后所有样板都推不出去，整条产线静默停摆。
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.DIVINE_MAX_LEVEL == 6,
                "The instant level must be level 6");
        org.zifeng.ae2skills.divine.DivineTranscend.reset();
        helper.assertTrue(!org.zifeng.ae2skills.divine.DivineTranscend.vetoPush(null),
                "A null pattern must never be vetoed");
        org.zifeng.ae2skills.divine.DivineTranscend.onPushed(null);
        org.zifeng.ae2skills.divine.DivineTranscend.onInsert(null, null, appeng.api.config.Actionable.MODULATE);
        org.zifeng.ae2skills.divine.DivineTranscend.reset();

        // 成就：JSON 必须真的被数据包加载，且存档级标记可持久化。
        var server = helper.getLevel().getServer();
        var holder = server.getAdvancements().get(org.zifeng.ae2skills.advancement.DivineAdvancements.ID);
        helper.assertTrue(holder != null, "The divine advancement must be loaded from the mod datapack");
        for (String path : new String[]{org.zifeng.ae2skills.SkillDefinitions.ACH_ASSEMBLER,
                org.zifeng.ae2skills.SkillDefinitions.ACH_CPU, org.zifeng.ae2skills.SkillDefinitions.ACH_TRANSCEND}) {
            helper.assertTrue(server.getAdvancements().get(org.zifeng.ae2skills.advancement.DivineAdvancements.id(path)) != null,
                    "Advancement " + path + " must be loaded from the mod datapack");
        }
        // 「合成之神 / 算力之神」只是搬进神明页签，升级消耗必须保持原来的非神明档位，
        // 否则把两个技能的成长曲线改掉了（isDivine 只对万机之神成立）。
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.inDivineTab(org.zifeng.ae2skills.SkillDefinitions.ASSEMBLER_INFINITE)
                        && org.zifeng.ae2skills.SkillDefinitions.inDivineTab(org.zifeng.ae2skills.SkillDefinitions.CPU_INFINITE),
                "Both renamed skills must live in the divine tab");
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.cost(org.zifeng.ae2skills.SkillDefinitions.ASSEMBLER_INFINITE, 0) == 2000L,
                "Moving a skill into the divine tab must not change its cost curve");
        // 「算力之神」5 级的费用 = 2000 << 4 = 32000（与上面 totals 里的 62000 自洽：
        // 2000+4000+8000+16000+32000）。原先这里写的是 64000，那是 6 级的价格，
        // 与本文件同一处的 totals 互相矛盾，属于从未被执行到位的错误断言。
        helper.assertTrue(org.zifeng.ae2skills.SkillDefinitions.cost(org.zifeng.ae2skills.SkillDefinitions.CPU_INFINITE, 4) == 32000L,
                "Moving a skill into the divine tab must not change its cost curve");
        var storage = server.overworld().getDataStorage();
        var state = storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                org.zifeng.ae2skills.advancement.DivineAdvancements.State::new,
                org.zifeng.ae2skills.advancement.DivineAdvancements.State::load),
                "zifeng_ae2skills_test_divine_state");
        var resaved = state.save(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(!resaved.getBoolean(org.zifeng.ae2skills.advancement.DivineAdvancements.GRANTED_KEY),
                "A fresh save must not have granted the advancement");
        // 刻意不动 Ae2Effects：它是进程级共享状态，而 GameTest 同批次并行执行。
        helper.succeed();
    }

    /**
     * 第三方 CPU 通用适配（形态 b）：一个<b>不继承</b> {@code CraftingCPUCluster} 的
     * {@link ICraftingCPU} 实现，正是原生 Mixin 命中不到、过去「装了别的模组就不生效」的那类。
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "zifeng_adapters")
    public static void genericThirdPartyCpuAdapters(GameTestHelper helper) {
        var thirdParty = new ThirdPartyCpu(4, 1024L);

        // 0) 技能全关（默认）→ 原样返回，零开销直通，行为与未装附属一致。
        Ae2Effects.update(SKILL_OWNER, 0, 0, 0, 0, 0);
        helper.assertTrue(CpuAdapterRegistry.wrap(thirdParty) == thirdParty, "All-off must pass the original CPU straight through");
        helper.assertTrue(!CpuAdapterRegistry.active(), "Adapter must be inactive while every skill is off");

        // 1) 保守模式（1.1.5 的核心回归防线）：集合里只要出现外来 CPU，
        //    wrapAll 必须<b>整体退出并原样返回同一个集合对象</b>，绝不逐个包装。
        //    1.1.4 正是在这里出错——包装制造出 ECO 的 hasCpu(==) 认不出的陌生引用，
        //    导致 CPU 列表点击无响应、合成状态不显示。
        Ae2Effects.update(SKILL_OWNER, 0, 2, 0, 5, 0);
        helper.assertTrue(CpuAdapterRegistry.active(), "Adapter must be active once the CPU skills are on");
        helper.assertTrue(CpuAdapterRegistry.isForeign(thirdParty), "A self-owned ICraftingCPU must be detected as foreign");
        var withForeign = com.google.common.collect.ImmutableSet.<ICraftingCPU>of(thirdParty);
        var result = CpuAdapterRegistry.wrapAll(withForeign, cpus -> {
            throw new AssertionError("Conservative mode must never rewrap a set containing a foreign CPU");
        });
        helper.assertTrue(result == withForeign,
                "A CPU set containing any foreign CPU must be returned unchanged (identity), never rewrapped");

        // 2) 装饰器本身的数值语义仍然正确（保留 wrap 供未来在确认安全时启用）。
        Ae2Effects.update(SKILL_OWNER, 0, 2, 0, 0, 0);
        var speedWrapped = CpuAdapterRegistry.wrap(thirdParty);
        helper.assertTrue(speedWrapped != thirdParty, "A third-party CPU must be wrapped while skills are on");
        helper.assertTrue(speedWrapped.getCoProcessors() > thirdParty.baseCoProcessors,
                "CPU overclock must raise a third-party CPU's co-processors");
        helper.assertTrue(speedWrapped.getCoProcessors() <= 4096, "Co-processor bonus must respect the parallel cap");
        helper.assertTrue(speedWrapped.getAvailableStorage() == thirdParty.baseStorage,
                "CPU overclock alone must not inflate storage");

        // 3) 分裂算力满级 → 容量无上限，并行数取近似无限。
        Ae2Effects.update(SKILL_OWNER, 0, 0, 0, 5, 0);
        var unlimitedWrapped = CpuAdapterRegistry.wrap(thirdParty);
        helper.assertTrue(unlimitedWrapped.getAvailableStorage() == Long.MAX_VALUE,
                "Infinite crafting must report unbounded storage for a third-party CPU");
        helper.assertTrue(unlimitedWrapped.getCoProcessors() == Integer.MAX_VALUE - 1,
                "Full split level must report near-unlimited co-processors");

        // 4) 其余方法必须逐一委托给 inner（用哨兵值可观测地验证，而不是只看类型）。
        Ae2Effects.update(SKILL_OWNER, 0, 2, 0, 1, 0);
        var wrapped = CpuAdapterRegistry.wrap(thirdParty);
        helper.assertTrue(thirdParty.busy && wrapped.isBusy(), "isBusy must delegate to the wrapped CPU");
        helper.assertTrue(wrapped.getJobStatus() == thirdParty.sentinelStatus, "getJobStatus must delegate to the wrapped CPU");
        helper.assertTrue(wrapped.getName() == thirdParty.sentinelName, "getName must delegate to the wrapped CPU");
        helper.assertTrue(wrapped.getSelectionMode() == thirdParty.sentinelMode, "getSelectionMode must delegate to the wrapped CPU");
        wrapped.cancelJob();
        helper.assertTrue(thirdParty.cancelled, "cancelJob must delegate to the wrapped CPU");

        // 5) 幂等：包装过的对象再次 wrap 不再嵌套放大。
        helper.assertTrue(CpuAdapterRegistry.wrap(wrapped) == wrapped, "Wrapping an already adapted CPU must be idempotent");

        // 6) wrap(null) 必须原样返回，绝不抛异常。
        helper.assertTrue(CpuAdapterRegistry.wrap(null) == null, "wrap(null) must return null");

        // 7) 第三方实现抛异常时：包装阶段绝不外抛；真正的异常在读取时原样透传给调用方
        //    （AE2 自行处理），本类不做吞异常这种会掩盖故障的「保护」。
        var broken = new ThirdPartyCpu(1, 1L) {
            @Override public long getAvailableStorage() { throw new IllegalStateException("third-party failure"); }
        };
        var brokenWrapped = CpuAdapterRegistry.wrap(broken);
        helper.assertTrue(brokenWrapped != null, "Wrapping a throwing third-party CPU must not propagate");
        try {
            brokenWrapped.getAvailableStorage();
            throw new AssertionError("Unreachable: the delegate must still throw when read");
        } catch (IllegalStateException expected) {
            // 预期：装饰器不吞异常，读取时透传 inner 的实现异常。
        }

        // 8) 原生 CPU 既不是外来者，也绝不被重新包装（身份必须稳定）。
        //    需要一台真正成形的集群，故与主回归同样搭一台最简 CPU 并等待其联网成形。
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        var fixture = new Fixture(helper);
        helper.startSequence()
                .thenWaitUntil(fixture::awaitNetwork)
                .thenExecute(() -> {
                    var nativeCpu = fixture.physical;
                    helper.assertTrue(!CpuAdapterRegistry.isForeign(nativeCpu),
                            "A native CraftingCPUCluster must not count as foreign");
                    helper.assertTrue(CpuAdapterRegistry.wrap(nativeCpu) == nativeCpu,
                            "A native CraftingCPUCluster must pass through unchanged to avoid double scaling");
                    // 全原生集合与混合集合都必须整体原样返回，绝不重建引用。
                    var allNative = com.google.common.collect.ImmutableSet.<ICraftingCPU>of(nativeCpu);
                    helper.assertTrue(CpuAdapterRegistry.wrapAll(allNative, cpus -> {
                        throw new AssertionError("An all-native set must not be rewrapped either");
                    }) == allNative, "An all-native CPU set must be returned unchanged");
                    var mixed = com.google.common.collect.ImmutableSet.<ICraftingCPU>of(nativeCpu, thirdParty);
                    helper.assertTrue(CpuAdapterRegistry.wrapAll(mixed, cpus -> {
                        throw new AssertionError("A mixed set must not be rewrapped");
                    }) == mixed, "A mixed CPU set must be returned unchanged");
                })
                .thenExecute(() -> Ae2Effects.remove(SKILL_OWNER))
                .thenSucceed();
    }

    /**
     * 形态 b 的 CPU：自有 {@link ICraftingCPU} 实现，不进 AE2 的 {@code craftingCPUClusters}。
     * 字段刻意 public 便于断言委托真实发生。
     */
    private static class ThirdPartyCpu implements ICraftingCPU {
        final int baseCoProcessors;
        final long baseStorage;
        boolean busy = true;
        boolean cancelled;
        final appeng.api.networking.crafting.CraftingJobStatus sentinelStatus =
                new appeng.api.networking.crafting.CraftingJobStatus(null, 0L, 0L, 0L);
        final Component sentinelName = Component.literal("third-party-cpu-sentinel");
        final appeng.api.config.CpuSelectionMode sentinelMode = appeng.api.config.CpuSelectionMode.PLAYER_ONLY;

        ThirdPartyCpu(int coProcessors, long storage) { this.baseCoProcessors = coProcessors; this.baseStorage = storage; }

        @Override public boolean isBusy() { return busy; }
        @Override public appeng.api.networking.crafting.CraftingJobStatus getJobStatus() { return sentinelStatus; }
        @Override public void cancelJob() { cancelled = true; }
        @Override public long getAvailableStorage() { return baseStorage; }
        @Override public int getCoProcessors() { return baseCoProcessors; }
        @Override public Component getName() { return sentinelName; }
        @Override public appeng.api.config.CpuSelectionMode getSelectionMode() { return sentinelMode; }
    }

    private static GenericStack stack(Item item, long count) { return new GenericStack(AEItemKey.of(item), count); }    private static KeyCounter keys(Item item, long count) { var result = new KeyCounter(); result.add(AEItemKey.of(item), count); return result; }
    private record TestPlan(GenericStack finalOutput, long bytes, boolean simulation, boolean multiplePaths,
                            KeyCounter usedItems, KeyCounter emittedItems, KeyCounter missingItems,
                            Map<IPatternDetails, Long> patternTimes) implements ICraftingPlan {}
    private static final class MemoryStorage implements MEStorage {
        final KeyCounter contents = new KeyCounter();
        boolean accept = true;
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!accept) return 0;
            if (mode == Actionable.MODULATE) contents.add(key, amount);
            return amount;
        }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long taken = Math.min(amount, contents.get(key));
            if (mode == Actionable.MODULATE) contents.remove(key, taken);
            return taken;
        }
        public void getAvailableStacks(KeyCounter out) { out.addAll(contents); }
        public Component getDescription() { return Component.literal("AE2 skill integration test inventory"); }
    }
}
