package org.zifeng.ae2skills.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.me.service.CraftingService;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.TickBudget;
import java.util.ArrayList;
import java.util.List;

/** Crafting-table patterns only. Processing patterns always stay in AE2's normal executor. */
public final class InstantCrafting {
    private InstantCrafting() {}
    public static boolean isDeliveringFinalOutput() { return CraftingDelivery.isDeliveringFinalOutput(); }

    /** Pattern cursor survives time slices; blocked early entries cannot starve a later recipe. */
    public static final class Cursor {
        private Object job;
        private List<IPatternDetails> patterns = List.of();
        private int next;
        private void clear() { job = null; patterns = List.of(); next = 0; }
    }

    public static void tick(CraftingCpuLogic logic, IEnergyService energy, CraftingService service) {
        tick(new NativeCpuExecution(logic), ((InstantCpuExtension) logic).zifeng$instantCursor(), energy, service);
    }

    public static void tick(CpuExecution logic, Cursor cursor, IEnergyService energy, CraftingService service) {
        int level = Ae2Effects.getAssemblerInfiniteLevel();
        Object originalJob = logic.skillJob();
        if (level == 0 || originalJob == null) { cursor.clear(); return; }
        if (logic.skillSuspended()) return;
        JobAccess job = (JobAccess) originalJob;
        if (!logic.skillActive() || job.zifeng$getLink().isCanceled()) return;

        long now = System.nanoTime();
        long deadline = TickBudget.deadline(now);
        if (deadline <= now) return;
        long quota = level == 5 ? Long.MAX_VALUE : Ae2Effects.getInstantOpsPerTick();
        try {
            if (cursor.job != originalJob) {
                cursor.job = originalJob;
                cursor.patterns = new ArrayList<>(job.zifeng$getTasks().keySet());
                cursor.next = 0;
            }
            int scanned = 0;
            boolean advanced = false;
            while (!cursor.patterns.isEmpty() && quota > 0 && System.nanoTime() < deadline) {
                IPatternDetails pattern = cursor.patterns.get(cursor.next);
                cursor.next = (cursor.next + 1) % cursor.patterns.size();
                Object progress = job.zifeng$getTasks().get(pattern);
                // A processing pattern can claim arbitrary outputs. Never execute one here.
                if (pattern instanceof IMolecularAssemblerSupportedPattern && progress instanceof TaskAccess task) {
                    long remaining = task.zifeng$getRemaining();
                    long completed = remaining <= 0 ? 0 : execute(logic, job, task, pattern,
                            Math.min(quota, remaining), energy, service);
                    advanced |= completed > 0;
                    quota -= completed;
                    if (logic.skillJob() != originalJob) { cursor.clear(); return; }
                    if (task.zifeng$getRemaining() == 0) job.zifeng$getTasks().remove(pattern);
                }
                if (++scanned == cursor.patterns.size()) {
                    if (!advanced) break;
                    scanned = 0;
                    advanced = false;
                }
            }
        } finally {
            TickBudget.charge(now);
        }
    }

    private static long execute(CpuExecution logic, JobAccess job,
                                TaskAccess task, IPatternDetails pattern, long maximum,
                                IEnergyService energy, CraftingService service) {
        boolean available = false;
        for (var provider : service.getProviders(pattern)) {
            if (!provider.isBusy()) { available = true; break; }
        }
        if (!available) return 0;
        ListCraftingInventory inventory = logic.skillInventory();
        KeyCounter outputs = new KeyCounter();
        KeyCounter containers = new KeyCounter();
        // AE2 validates substitutions and determines the actual container keys for these inputs.
        KeyCounter[] inputs = CraftingCpuHelper.extractPatternInputs(pattern, inventory,
                logic.skillLevel(), outputs, containers);
        if (inputs == null) return 0;
        boolean committed = false;
        try {
            KeyCounter consumed = new KeyCounter();
            for (KeyCounter input : inputs) consumed.addAll(input);
            long count = maximum;
            for (var input : consumed) {
                if (input.getLongValue() > 0) {
                    count = Math.min(count, 1 + logic.skillStored(input.getKey()) / input.getLongValue());
                }
            }
            KeyCounter produced = new KeyCounter();
            produced.addAll(outputs);
            produced.addAll(containers);
            for (var output : produced) {
                long each = output.getLongValue();
                if (each <= 0) continue;
                // All AE2 counters are signed longs. Bound a batch before any multiplication/addition.
                long pending = job.zifeng$getWaitingFor().list.get(output.getKey());
                count = Math.min(count, (Long.MAX_VALUE - Math.max(0, pending)) / each);
                count = Math.min(count, (Long.MAX_VALUE - Math.max(0, logic.skillStored(output.getKey()))) / each);
            }
            double perCraft = CraftingCpuHelper.calculatePatternPower(inputs);
            if (perCraft > 0 && count > 0) {
                double supplied = energy.extractAEPower(perCraft * count, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                count = Math.min(count, (long) Math.floor((supplied + 0.001D) / perCraft));
            }
            if (count <= 0) return 0;
            // No external machine receives these materials. One batch consumes each ingredient once.
            for (var input : consumed) {
                inventory.extract(input.getKey(), input.getLongValue() * (count - 1), Actionable.MODULATE);
            }
            energy.extractAEPower(perCraft * count, Actionable.MODULATE, PowerMultiplier.CONFIG);
            task.zifeng$setRemaining(task.zifeng$getRemaining() - count);
            committed = true;
            for (var remainder : containers) {
                logic.skillAddMaxItems(remainder.getLongValue() * count, remainder.getKey().getType());
            }
            for (var output : produced) {
                job.zifeng$getWaitingFor().insert(output.getKey(), output.getLongValue() * count, Actionable.MODULATE);
            }
            AEKey finalKey = logic.skillFinalOutput().what();
            // Return containers/byproducts before the last output can finish and clear the job.
            for (var output : produced) {
                if (!output.getKey().equals(finalKey)) deliver(logic, output.getKey(), output.getLongValue() * count);
            }
            long finalAmount = produced.get(finalKey) * count;
            if (finalAmount > 0) deliver(logic, finalKey, finalAmount);
            logic.skillMarkDirty();
            return count;
        } finally {
            if (!committed) CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
        }
    }

    /** 三级兜底投递已抽到 {@link CraftingDelivery}，与「万机之神的加护 6 级瞬成」共用同一份。 */
    private static void deliver(CpuExecution logic, AEKey key, long amount) {
        CraftingDelivery.deliver(logic, key, amount);
    }
}
