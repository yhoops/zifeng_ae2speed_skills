package org.zifeng.ae2skills.mixin.ae2;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.CpuExtension;
import org.zifeng.ae2skills.crafting.VirtualCpus;

/** CPU overclocking is per job; split CPUs share their physical storage pool. */
@Mixin(value = CraftingCPUCluster.class, priority = 900, remap = false)
public abstract class CraftingCpuSpeedMixin {
    /**
     * 拿到集群上的扩展契约，没接上就返回 null。
     * <p>主配置 1.2.1 起注入失败只降级不崩（见 {@code MixinFailureHandler}），因此必须考虑
     * 「本混入挂上了、{@code AeCpuClusterMixin} 没挂上」的半应用状态。那种情况下如果照旧
     * 强制转换，每一 tick 的 {@code getCoProcessors} 都会抛 {@code ClassCastException}；
     * 返回 null 后两条注入都退回「原样不改」，玩家看到的仍是一台能正常工作的 CPU。
     */
    private static CpuExtension zifeng$contract(Object cluster) {
        return cluster instanceof CpuExtension extension ? extension : null;
    }

    @Inject(method = "getCoProcessors", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$scaleCoProcessors(CallbackInfoReturnable<Integer> ci) {
        var self = (CraftingCPUCluster) (Object) this;
        var extension = zifeng$contract(self);
        if (extension == null || extension.zifeng$readingBase()) return;
        if (extension.zifeng$state().externallyManaged) return;
        ci.setReturnValue(VirtualCpus.coProcessors(self));
    }

    @Inject(method = "getAvailableStorage", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$sharedStorage(CallbackInfoReturnable<Long> ci) {
        var self = (CraftingCPUCluster) (Object) this;
        var extension = zifeng$contract(self);
        if (extension == null || extension.zifeng$readingBase()) return;
        if (extension.zifeng$state().externallyManaged) return;
        ci.setReturnValue(VirtualCpus.availableStorage(self));
    }
}
