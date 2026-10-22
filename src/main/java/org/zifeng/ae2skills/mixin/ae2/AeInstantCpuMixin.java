package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.ae2skills.crafting.InstantCrafting;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.VirtualCpus;
import org.zifeng.ae2skills.crafting.InstantCpuExtension;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class AeInstantCpuMixin implements InstantCpuExtension {
    @Unique private final InstantCrafting.Cursor zifeng$cursor = new InstantCrafting.Cursor();
    public InstantCrafting.Cursor zifeng$instantCursor() { return zifeng$cursor; }
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), require = 1)
    private void zifeng$instantCrafting(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        InstantCrafting.tick((CraftingCpuLogic) (Object) this, energy, service);
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$guardSlot(IGrid grid, ICraftingPlan plan, IActionSource source,
                                ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> ci) {
        var cluster = ((CpuLogicAccess) this).zifeng$getCluster();
        if (!VirtualCpus.canSubmit(cluster, grid)) ci.setReturnValue(CraftingSubmitResult.CPU_BUSY);
    }
    @Inject(method = "trySubmitJob", at = @At("RETURN"), require = 1)
    private void zifeng$reserveCapacity(IGrid grid, ICraftingPlan plan, IActionSource source,
                                       ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> ci) {
        if (ci.getReturnValue().successful()) {
            var cluster = ((CpuLogicAccess) this).zifeng$getCluster();
            VirtualCpus.state(cluster).reservedBytes = Math.max(0, plan.bytes());
            cluster.markDirty();
        }
    }
}
