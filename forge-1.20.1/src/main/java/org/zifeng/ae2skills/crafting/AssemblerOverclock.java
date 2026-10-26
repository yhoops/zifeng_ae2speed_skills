package org.zifeng.ae2skills.crafting;

import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.TickBudget;
import org.zifeng.ae2skills.divine.DivineGrace;

/**
 * Extra native work cycles: each cycle retains the owning machine's power/material/output rules.
 *
 * <h2>通用性（不依赖模组类名）</h2>
 * 挂钩点是 {@code TickManagerService.unsafeTickingRequest}，它对任意 {@link IGridTickable} 生效；
 * 判定则走本类的 {@link #supports(IGridTickable, IGridNode)}，主路径是 AE2 官方接口
 * {@link ICraftingMachine#acceptsPlans()} —— <b>任何</b>第三方「能被供应模板的装配机」都满足它，
 * 不需要知道它叫什么类。因此新增模组无需改本类。
 * <p>{@code mixin/compat} 下那几个写死类名的 Mixin 只是「已知模组的精确适配（兜底）」，
 * 用于避免已实测环境行为变化，而<b>不是</b>唯一入口。
 */
public final class AssemblerOverclock {
    private AssemblerOverclock() {}

    /**
     * 判定顺序有语义，勿随意调整：
     * <ol>
     *   <li>{@link MolecularAssemblerBlockEntity} —— AE2 原生分子装配室的进度钩子<b>已经</b>加过乘数，
     *       这里必须返回 false 否则会双倍加速。</li>
     *   <li>{@link AssemblerExecution} —— 由写死兼容 Mixin 挂上的显式标记，表示「本机已确认是装配机」。</li>
     *   <li>{@link ICraftingMachine#acceptsPlans()} —— <b>通用主路径</b>：AE2 官方接口，模组无关。
     *       第三方的装配机只要实现 ICraftingMachine 且接受模板，就自动被超频。</li>
     * </ol>
     */
    public static boolean supports(IGridTickable tickable, IGridNode node) {
        // AE2's native progress hook already applies the multiplier; never double it.
        if (tickable instanceof MolecularAssemblerBlockEntity) return false;
        if (tickable instanceof AssemblerExecution) return true;
        // 通用主路径：AE2 官方接口判定，任意第三方装配机在此生效。
        return tickable instanceof ICraftingMachine machine && machine.acceptsPlans();
    }

    public static TickRateModulation tick(IGridTickable tickable, IGridNode node, int elapsed) {
        var result = tickable.tickingRequest(node, elapsed);
        int bonus = Ae2Effects.getAssemblerSpeedLevel();
        if (bonus == 0 || result == TickRateModulation.SLEEP || result == TickRateModulation.IDLE
                || !supports(tickable, node)) return result;
        // 「万机之神的加护」本 tick 已经在驱动这台机器时让路：两个技能不叠加同一份额外 tick。
        if (tickable instanceof BlockEntity blockEntity && DivineGrace.isAccelerated(blockEntity)) return result;
        long began = System.nanoTime();
        long deadline = TickBudget.deadline(began);
        try {
            for (int i = 0; i < bonus && System.nanoTime() < deadline; i++) {
                if (!node.isActive()) break;
                result = tickable.tickingRequest(node, elapsed);
                if (result == TickRateModulation.SLEEP || result == TickRateModulation.IDLE) break;
            }
            return result;
        } finally {
            TickBudget.charge(began);
        }
    }
}
