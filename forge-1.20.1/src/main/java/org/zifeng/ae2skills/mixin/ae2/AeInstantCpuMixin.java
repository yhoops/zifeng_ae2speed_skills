package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.InstantCpuExtension;
import org.zifeng.ae2skills.crafting.InstantCrafting;
import org.zifeng.ae2skills.crafting.VirtualCpus;

/**
 * 瞬成与本混入的入口。
 * <p>1.2.1 起主配置注入失败只降级不崩，就必须考虑「本混入挂上了、{@code CpuLogicAccess}
 * 或 {@code AeCpuClusterMixin} 没挂上」的半应用状态：那种情况下功能已经整体停用
 * （技能等级发布成 0），这里再去做强制转换只会把服务器 tick 打断。因此每个入口都先取
 * 契约、取不到就直接返回原版行为。
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class AeInstantCpuMixin implements InstantCpuExtension {
    @Unique private final InstantCrafting.Cursor zifeng$cursor = new InstantCrafting.Cursor();
    public InstantCrafting.Cursor zifeng$instantCursor() { return zifeng$cursor; }

    /** 本对象的访问器契约；{@code CpuLogicAccess} 没挂上时返回 null。 */
    @Unique private CpuLogicAccess zifeng$access() {
        return this instanceof CpuLogicAccess access ? access : null;
    }

    /** 本对象的集群；任一环节缺契约都返回 null。 */
    @Unique private CraftingCPUCluster zifeng$cluster() {
        var access = zifeng$access();
        return access == null ? null : access.zifeng$getCluster();
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), require = 1)
    private void zifeng$instantCrafting(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        InstantCrafting.tick((CraftingCpuLogic) (Object) this, energy, service);
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$guardSlot(IGrid grid, ICraftingPlan plan, IActionSource source,
                                ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> ci) {
        var cluster = zifeng$cluster();
        if (cluster != null && !VirtualCpus.canSubmit(cluster, grid)) ci.setReturnValue(CraftingSubmitResult.CPU_BUSY);
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"), require = 1)
    private void zifeng$reserveCapacity(IGrid grid, ICraftingPlan plan, IActionSource source,
                                       ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> ci) {
        var cluster = zifeng$cluster();
        var result = ci.getReturnValue();
        if (cluster == null || result == null || !result.successful()) return;
        // state() 在半应用状态下返回共享占位，写入会泄漏到所有 CPU，因此走可写入口。
        var state = VirtualCpus.mutableState(cluster);
        if (state == null) return;
        state.reservedBytes = Math.max(0, plan.bytes());
        cluster.markDirty();
    }
}
