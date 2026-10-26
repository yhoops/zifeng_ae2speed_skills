package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.divine.DivineTranscend;

/**
 * 「万机之神的加护」6 级瞬成的三个观察点，全部挂在 AE2 原生合成 CPU 上：
 * <ul>
 *   <li>{@code executeCrafting} 进出：标记「此刻是这台 CPU 在推样板」，让推送侧能定位账本；</li>
 *   <li>{@code insert} 返回：<b>严格验证</b>——产物真的被合成任务接纳了；</li>
 *   <li>{@code tickCraftingLogic} 开头：结算剩余批次（刻意不在 insert 的调用栈里重入）。</li>
 * </ul>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class DivineCpuMixin {
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), require = 1)
    private void zifeng$transcendTick(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        // 每个 tick 的起点先把「正在推送的 CPU」清空：executeCrafting 抛异常时 RETURN 注入不会执行，
        // 残留的旧 CPU 会让无关产线被永久否决、取证也会记到别人的账本上。
        DivineTranscend.resetPushing();
        DivineTranscend.tick((CraftingCpuLogic) (Object) this);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), require = 1)
    private void zifeng$enterPush(int maxPatterns, CraftingService service, IEnergyService energy,
                                  Level level, CallbackInfoReturnable<Integer> ci) {
        DivineTranscend.enterPush((CraftingCpuLogic) (Object) this);
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), require = 1)
    private void zifeng$exitPush(int maxPatterns, CraftingService service, IEnergyService energy,
                                 Level level, CallbackInfoReturnable<Integer> ci) {
        DivineTranscend.exitPush();
    }

    @Inject(method = "insert", at = @At("HEAD"), require = 1)
    private void zifeng$snapshotWaitingFor(AEKey key, long amount, Actionable mode,
                                           CallbackInfoReturnable<Long> ci) {
        DivineTranscend.beginInsert((CraftingCpuLogic) (Object) this, key);
    }

    @Inject(method = "insert", at = @At("RETURN"), require = 1)
    private void zifeng$observeProductReturn(AEKey key, long amount, Actionable mode,
                                             CallbackInfoReturnable<Long> ci) {
        DivineTranscend.onInsert((CraftingCpuLogic) (Object) this, key, mode);
    }
}
