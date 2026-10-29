package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.CraftingDelivery;
import org.zifeng.ae2skills.divine.DivineTranscend;

/**
 * 兄弟 CPU 不重复结清最终产物。
 *
 * <h2>为什么挂在被调方而不是调用方（1.2.1 修复 issue #1）</h2>
 * 这条去重原本是 {@code CraftingService.insertIntoCpus} 里
 * {@code CraftingCpuLogic.insert} 调用点的 {@code @Redirect}。Mixin 的规则是：
 * 目标方法一旦被别的混入以更高 priority {@code @Overwrite} 合并，低优先级的注入器
 * <b>直接抛 InvalidInjectionException</b>，而本附属主配置是 {@code required:true}，
 * 结果就是游戏起不来。
 * <p>实测冲突源：crazyae2addons 2.6.2 的 {@code AAECraftingServiceMixin}（priority 1200）
 * 用 {@code @Overwrite} 重写了 {@code insertIntoCpus} 的整段方法体。它的字节码里仍然是对
 * {@code craftingLogic.insert(what, left, mode)} 的虚调用，因此把去重挪到
 * {@link CraftingCpuLogic#insert} 的 {@code HEAD}：
 * <ul>
 *   <li>无论调用方是原生实现还是任意第三方 {@code @Overwrite}，最终都会调用本方法，
 *       注入点<b>天然免疫调用方被覆盖</b>；</li>
 *   <li>本方法在 AE2 15.4.11 中没有任何 {@code @Overwrite}，不存在 merged 优先级检查；</li>
 *   <li>语义等价：原 {@code @Redirect} 只在「最终产物落进 ME 存储后回流其他 CPU」这一小段
 *       生效，{@link CraftingDelivery} 的 {@code finalDepth} 窗口恰好只覆盖那一段。</li>
 * </ul>
 *
 * <h2>为什么 cancel 前要清取证帧</h2>
 * {@link DivineCpuMixin} 对本方法也有一条 {@code HEAD} 注入，会在
 * {@link DivineTranscend} 里压入一帧「insert 前的 waitingFor 快照」，由 {@code RETURN}
 * 注入弹出。本注入是 {@code cancellable}，一旦取消，方法直接返回、{@code RETURN} 注入
 * 不会执行，那一帧就会残留并污染下一次取证。两条 {@code HEAD} 注入的先后顺序由 Mixin 内部
 * 排序决定，无法从源码保证，因此这里主动丢弃残留帧。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class AeCpuInsertMixin {
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$ownFinalOutput(AEKey key, long amount, Actionable mode,
                                       CallbackInfoReturnable<Long> cir) {
        if (key == null || amount <= 0) return;
        if (!CraftingDelivery.isDeliveringFinalOutput()) return;
        // 完成度已记在产出它的那台 CPU 上。不要让一台独立的兄弟 CPU 用同一批物品
        // 把自己的订单也判成完成（原生 insert 在这种情况下返回 0）。
        if (!key.matches(((CraftingCpuLogic) (Object) this).getFinalJobOutput())) return;
        DivineTranscend.discardPendingInsert((CraftingCpuLogic) (Object) this, key);
        cir.setReturnValue(0L);
    }
}
