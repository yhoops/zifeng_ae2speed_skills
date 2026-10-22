package org.zifeng.ae2skills.test;

import appeng.core.definitions.AEBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.energy.AePowerHolder;
import org.zifeng.ae2skills.energy.AePowerSource;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.init.ModBlocks;

/**
 * AE 供电的端到端验证：真实本体的技能点转换机 + 真实 AE2 能量元件组成的 ME 网络。
 *
 * <p>覆盖：默认关闭、互斥关闭 FE 入口、未连接不抽电、相邻接入、AE 经汇率折算后真的推进本体账本、
 * 抽取永不超过本体缓冲上限、速率框确实限速、无限制开关确实抬高该上限、断线恢复、NBT 往返保持开关。
 *
 * <p>「连接但缺电时保留进度」由实现保证（取电前刷新 {@code lastReceiveTick}，早于本体空闲判定）
 * 并经代码审查确认；测试环境无法稳定构造「连着但一点电都给不出」的状态，因此该项不在此断言。
 *
 * <p>注意 1：{@code GameTestHelper.setBlock/getBlockEntity} 用**相对**坐标，
 * 而世界的 capability 查询与生产代码用**绝对**坐标（{@code getBlockPos()}），
 * 因此这里一律经 {@link GameTestHelper#absolutePos} 换算后再查世界。
 *
 * <p>注意 2：本体把「本 tick 已接收量」记在**游戏 tick** 上（{@code receivedThisTick}），
 * 所以速率档与无限制档必须各自跨真实 tick 采样，不能在同一 tick 内连测。
 */
@GameTestHolder(Addon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AePowerGameTests {
    private static final UUID OWNER = UUID.fromString("5c2bb0a1-2f18-4c7e-9f4d-1a6b3e8d0f21");
    private static final BlockPos CONVERTER = new BlockPos(2, 1, 2);
    private static final BlockPos CELL = new BlockPos(3, 1, 2);

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void aePowerEndToEnd(GameTestHelper helper) {
        long[] convertedBefore = new long[1];
        long[] rateBound = new long[1];
        helper.startSequence()
                // 1) 默认状态：开关关闭、FE 入口开放、相邻没有 AE 设备时未连接
                .thenExecute(() -> {
                    helper.setBlock(CONVERTER, ModBlocks.STAR_ENERGY_CONVERTER.get());
                    seen(helper).setOwnerUUID(OWNER);
                    helper.assertTrue(!holder(helper).ae2skills$isAePowerEnabled(),
                            "AE power must default to off");
                    helper.assertTrue(seen(helper).getEnergyStorage().canReceive(),
                            "FE input must stay open while AE power is off");
                    helper.assertTrue(!connected(helper),
                            "A converter with no adjacent AE device must report disconnected");
                })
                // 2) 互斥：开启 AE 后 FE 入口关闭，且真的收不进 FE
                .thenExecute(() -> {
                    holder(helper).ae2skills$setAePowerEnabled(true);
                    helper.assertTrue(!seen(helper).getEnergyStorage().canReceive(),
                            "Enabling AE power must close the FE input (sources are mutually exclusive)");
                    helper.assertTrue(seen(helper).getEnergyStorage().receiveEnergy(1000, false) == 0,
                            "Gated FE storage must reject actual FE while AE power is on");
                })
                // 3) 未连接时即使开了 AE，也不产点、不推进度
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(
                        seen(helper).getProgress() == 0 && seen(helper).getTotalConverted() == 0,
                        "A disconnected AE mode must draw nothing"))
                // 4) 相邻接入 ME 网络（紧贴一个 AE2 能量元件）
                .thenExecute(() -> helper.setBlock(CELL, AEBlocks.CREATIVE_ENERGY_CELL.block()))
                .thenWaitUntil(() -> helper.assertTrue(connected(helper),
                        "Converter must detect the adjacent ME network once the grid boots"))
                // 5) 端到端：网络里的 AE 经 AE2 汇率折算后真的写进本体账本
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> helper.assertTrue(seen(helper).getProgress() > 0,
                        "AE drawn from the network must advance the host progress ledger"
                                + " (connected=" + connected(helper)
                                + ", progress=" + seen(helper).getProgress()
                                + ", threshold=" + seen(helper).getContainerData().get(3)
                                + ", inputRate=" + seen(helper).getInputRate()
                                + ", converted=" + seen(helper).getTotalConverted() + ")"))
                // 6) 抽取量永不超过本体的缓冲上限（不会抽出网络能量却记不上账）
                .thenExecute(() -> helper.assertTrue(
                        seen(helper).getProgress() <= seen(helper).getCapacityLong(),
                        "AE draw must never exceed what the host can account for"))
                // 7) 速率框档：把速率框压到 100 FE/t，一个真实 tick 窗口内必须被它卡住，
                //    且慢到连一个技能点都凑不满（threshold 默认 1 万）。
                .thenExecute(() -> {
                    clearProgress(helper);
                    seen(helper).setInputRate(RATE_BOX);
                    seen(helper).setUnlimitedInput(false);
                    convertedBefore[0] = seen(helper).getTotalConverted();
                })
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    rateBound[0] = seen(helper).getProgress();
                    helper.assertTrue(rateBound[0] > 0,
                            "A connected, powered network must feed the converter every tick"
                                    + " (rate-bound=" + rateBound[0] + ")");
                    // 边界 tick 可能多注入一次，因此留一档余量；越界即说明速率框没生效
                    helper.assertTrue(rateBound[0] <= RATE_BOX * (WINDOW_TICKS + 2),
                            "The GUI rate box must cap the AE draw too (rate-bound="
                                    + rateBound[0] + ", cap=" + RATE_BOX * (WINDOW_TICKS + 2) + ")");
                    helper.assertTrue(seen(helper).getTotalConverted() == convertedBefore[0],
                            "The rate box must be slow enough that no skill point is minted in the window");
                })
                // 8) 无限制档：解除速率框，同一窗口内必须抽得更多，并真的产出技能点
                .thenExecute(() -> {
                    clearProgress(helper);
                    seen(helper).setUnlimitedInput(true);
                    convertedBefore[0] = seen(helper).getTotalConverted();
                })
                .thenIdle(WINDOW_TICKS)
                .thenExecute(() -> {
                    seen(helper).setUnlimitedInput(false);
                    long unlimited = seen(helper).getProgress();
                    helper.assertTrue(seen(helper).getTotalConverted() > convertedBefore[0],
                            "Unlimited input must raise the AE draw cap above the host input rate"
                                    + " (rate-bound=" + rateBound[0] + ", progress=" + unlimited
                                    + ", unlimited-converted="
                                    + (seen(helper).getTotalConverted() - convertedBefore[0]) + ")");
                })
                // 9) NBT 往返保留开关（本体成员是 protected，测试内经反射探针调用）
                .thenExecute(() -> {
                    CompoundTag saved = SaveProbe.save(seen(helper));
                    helper.assertTrue(saved.getBoolean("Ae2UseAeEnergy"),
                            "AE power flag must persist in block NBT");
                    holder(helper).ae2skills$setAePowerEnabled(false);
                    SaveProbe.load(seen(helper), saved);
                    helper.assertTrue(holder(helper).ae2skills$isAePowerEnabled(),
                            "AE power flag must survive an NBT round-trip");
                })
                // 10) 关闭后 FE 入口恢复
                .thenExecute(() -> {
                    holder(helper).ae2skills$setAePowerEnabled(false);
                    helper.assertTrue(seen(helper).getEnergyStorage().canReceive(),
                            "Disabling AE power must reopen the FE input");
                })
                // 11) 断开相邻设备后回到未连接
                .thenExecute(() -> helper.setBlock(CELL, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!connected(helper),
                        "Removing the adjacent AE device must report disconnected"))
                .thenSucceed();
    }

    /** 速率框测试值（FE/t）；远小于阶梯起始消耗（1 万），窗口内凑不满一个技能点。 */
    private static final long RATE_BOX = 100;
    /** 速率对比窗口（tick）：两档各自跨真实 tick 采样，避免共用同一 tick 的接收计数。 */
    private static final int WINDOW_TICKS = 20;

    /** 进度清零：本体字段是 private，测试内经反射探针改写（生产代码不放宽可见性）。 */
    private static void clearProgress(GameTestHelper helper) {
        SaveProbe.setLong(seen(helper), "progress", 0L);
    }

    private static SkillPointConverterBlockEntity seen(GameTestHelper helper) {
        return (SkillPointConverterBlockEntity) helper.getBlockEntity(CONVERTER);
    }

    private static AePowerHolder holder(GameTestHelper helper) {
        return (AePowerHolder) seen(helper);
    }

    private static boolean connected(GameTestHelper helper) {
        return AePowerSource.isConnected(helper.getLevel(), helper.absolutePos(CONVERTER));
    }
}
