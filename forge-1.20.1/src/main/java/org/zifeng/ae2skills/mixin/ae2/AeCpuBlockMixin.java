package org.zifeng.ae2skills.mixin.ae2;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.ae2skills.crafting.VirtualCpus;

@Mixin(value = CraftingBlockEntity.class, remap = false)
public abstract class AeCpuBlockMixin {
    @Shadow public abstract CraftingCPUCluster getCluster();
    @Inject(method = "breakCluster", at = @At("HEAD"), require = 1)
    private void zifeng$refundChildren(CallbackInfo ci) {
        if (getCluster() != null) VirtualCpus.beforeBreak(getCluster());
    }
}
