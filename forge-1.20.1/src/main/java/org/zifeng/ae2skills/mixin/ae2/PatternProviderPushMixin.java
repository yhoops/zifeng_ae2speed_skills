package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.divine.DivineGrace;
import org.zifeng.ae2skills.divine.DivineTranscend;

/**
 * 产线发现：AE2 及所有基于 {@link PatternProviderLogic} 的供应器都在这里把样板交给机器。
 * 只观察「成功推给机器」这件事，用来判定哪些供应器此刻真的在为活跃任务供货，
 * 进而得到「万机之神的加护」的加速目标集合。
 *
 * <p>6 级瞬成在此<b>同时</b>上锁：取证期间的样板不允许再推出去（见
 * {@link DivineTranscend#vetoPush}），否则一个 tick 内就会有多批在飞，
 * 「按剩余批数结清」会把这批重复记账、直接超产。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderPushMixin {
    @Shadow @Final private PatternProviderLogicHost host;

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$vetoLockedPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                          CallbackInfoReturnable<Boolean> ci) {
        // 返回 false 只表示「这个供应器这次没接」，AE2 会原样换下一个供应器，不算出错。
        if (DivineTranscend.vetoPush(patternDetails)) ci.setReturnValue(false);
    }

    @Inject(method = "pushPattern", at = @At("RETURN"), require = 1)
    private void zifeng$observeMachinePush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
                                            CallbackInfoReturnable<Boolean> ci) {
        if (!Boolean.TRUE.equals(ci.getReturnValue())) return;
        BlockEntity provider = host.getBlockEntity();
        if (provider != null) DivineGrace.onPush(provider);
        DivineTranscend.onPushed(patternDetails);
    }
}
