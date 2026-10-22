package org.zifeng.ae2skills.mixin.ae2;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.VirtualCpus;

/** CPU overclocking is per job; split CPUs share their physical storage pool. */
@Mixin(value = CraftingCPUCluster.class, priority = 900, remap = false)
public abstract class CraftingCpuSpeedMixin {
    @Inject(method = "getCoProcessors", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$scaleCoProcessors(CallbackInfoReturnable<Integer> ci) {
        if (((org.zifeng.ae2skills.crafting.CpuExtension) this).zifeng$readingBase()) return;
        if (VirtualCpus.state((CraftingCPUCluster) (Object) this).externallyManaged) return;
        ci.setReturnValue(VirtualCpus.coProcessors((CraftingCPUCluster) (Object) this));
    }
    @Inject(method = "getAvailableStorage", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$sharedStorage(CallbackInfoReturnable<Long> ci) {
        if (((org.zifeng.ae2skills.crafting.CpuExtension) this).zifeng$readingBase()) return;
        if (VirtualCpus.state((CraftingCPUCluster) (Object) this).externallyManaged) return;
        ci.setReturnValue(VirtualCpus.availableStorage((CraftingCPUCluster) (Object) this));
    }
}
