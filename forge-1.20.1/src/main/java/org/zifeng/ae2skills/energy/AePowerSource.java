package org.zifeng.ae2skills.energy;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
// 1.20.1：AE2 15.x 里这个枚举叫 PowerUnits（复数），常量与方法签名相同
import appeng.api.config.PowerUnits;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * AE 供电的取电实现：只从**相邻 6 面**发现 ME 网络（与 AE2 自身的相邻语义一致）。
 *
 * <p>汇率直接使用 AE2 的 {@link PowerUnits}，不硬编码数字，因此玩家修改 AE2 的
 * {@code powerRatios} 配置时会自动跟随（1 AE = 2 FE 只是默认值）。
 *
 * <p>不做让路或抢占：与本网络中其他用电器平等竞争，由 AE2 自己的分配逻辑决定结果。
 */
public final class AePowerSource {
    private AePowerSource() {}

    /**
     * 相邻 6 面里第一张可用的 ME 网络；没有则返回 null（= 未连接）。
     *
     * <p>刻意不检查 {@code isNetworkPowered()}：网络暂时缺电只代表「抽不到」，不代表「断开」。
     * 抽不到时 {@link #draw} 自然返回 0，进度保留（见「连接保持」）。
     */
    public static IEnergyService find(Level level, BlockPos pos) {
        if (level == null) return null;
        for (Direction side : Direction.values()) {
            // 邻居需要对「朝向本方块的那一面」暴露网格节点
            IGridNode node = GridHelper.getExposedNode(level, pos.relative(side), side.getOpposite());
            if (node == null || !node.isActive()) continue;
            IGrid grid = node.getGrid();
            if (grid == null) continue;
            IEnergyService energy = grid.getEnergyService();
            if (energy != null) return energy;
        }
        return null;
    }

    /** 相邻是否已接入 ME 网络（与是否有电无关）。 */
    public static boolean isConnected(Level level, BlockPos pos) {
        return find(level, pos) != null;
    }

    /** FE 当量 → 需要抽取的 AE 量。 */
    public static double aeForFe(double fe) {
        return PowerUnits.FE.convertTo(PowerUnits.AE, fe);
    }

    /** 实际抽到的 AE → FE 当量（喂进转换机的账本）。 */
    public static double feForAe(double ae) {
        return PowerUnits.AE.convertTo(PowerUnits.FE, ae);
    }

    /**
     * 真抽 AE。使用 {@link PowerMultiplier#CONFIG} 与 AE2 其他用电机器保持一致：
     * CONFIG 会同比缩放请求量与返回值，因此返回的仍是真实抽取量。
     */
    public static double draw(IEnergyService energy, double aeAmount) {
        if (energy == null || aeAmount <= 0) return 0;
        return energy.extractAEPower(aeAmount, Actionable.MODULATE, PowerMultiplier.CONFIG);
    }
}
