package org.zifeng.ae2skills.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.zifeng.ae2skills.Ae2Effects;

/**
 * 「装配超频」（AE 合成速度）技能钩子 —— 加速处理合成样板的机器（分子装配室）。
 *
 * <h2>挂钩点（AE2 19.2.17 实测反编译，勿凭记忆改）</h2>
 * <pre>
 *   appeng.blockentity.crafting.MolecularAssemblerBlockEntity
 *     private int userPower(int a, int b, double c) {
 *         return (int)(grid.getEnergyService().extractAEPower(a * b * c, MODULATE, CONFIG) / c);
 *     }
 *     // tickingRequest(...) 内 6 处调用（0~5 张加速卡），调用后：
 *     progress += userPower(...);          // 进度增量 = a * b（满电时）
 *     if (progress >= 100.0) { progress = 0; ...组装一次...; }
 * </pre>
 * 6 档 <b>b/c</b>：{@code 10/1.0, 13/1.3, 17/1.7, 20/2.0, 25/2.5, 50/5.0}。
 *
 * <h2>为什么改参数 b 而不是改返回值</h2>
 * 请求能量 = {@code a*b*c}，返回值（进度增量）= {@code 抽取量 / c}。
 * 把 <b>b</b> 乘 N ⇒ <b>进度 ×N 且请求能量 ×N</b>（单件总耗能不变，不是免费加速）；
 * 若只乘返回值，则能量不变而进度 ×N ⇒ 白白变成"省电挂"。
 *
 * <h2>为什么钳到 100</h2>
 * AE2 在 {@code progress >= 100} 时**先置 0 再组装**，即单台机器**每 tick 最多产出 1 件**，
 * 超过 100 的进度会被直接丢弃。不钳制的话，多出来的那部分只会按 b 放大后的功率白烧电。
 *
 * Compatibility is checked before all addon mixins; this injection is required.
 */
@Mixin(targets = "appeng.blockentity.crafting.MolecularAssemblerBlockEntity", remap = false)
public abstract class MolecularAssemblerSpeedMixin {

    /** 单台机器每 tick 的进度上限（= 1 件/tick，见类注释） */
    private static final int MAX_PROGRESS_PER_TICK = 100;

    /**
     * 把分子装配室的进度/能量系数 {@code b} 按「装配超频」倍率放大。
     *
     * @param base AE2 传入的档位系数（10/13/17/20/25/50）
     * @return 放大并钳制后的系数（倍率 ≤1 时原值返回，零开销直通）
     */
    @ModifyVariable(
            method = "userPower(IID)I",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2,
            require = 1
    )
    private int zifeng$scaleAssemblerProgress(int base) {
        double mult = Ae2Effects.getAssemblerSpeedMultiplier();
        if (mult <= 1.0) {
            return base; // 无人开启技能：完全等价原版
        }
        long scaled = Math.round(base * mult);
        if (scaled > MAX_PROGRESS_PER_TICK) {
            scaled = MAX_PROGRESS_PER_TICK;
        }
        return (int) scaled;
    }
}
