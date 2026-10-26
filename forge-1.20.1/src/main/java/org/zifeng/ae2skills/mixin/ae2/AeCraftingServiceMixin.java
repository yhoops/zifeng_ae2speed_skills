package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.compat.CpuAdapterRegistry;
import org.zifeng.ae2skills.crafting.VirtualCpus;
import org.zifeng.ae2skills.crafting.InstantCrafting;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

@Mixin(value = CraftingService.class, remap = false)
public abstract class AeCraftingServiceMixin {
    @Shadow @Final private Set<CraftingCPUCluster> craftingCPUClusters;
    @Unique private boolean zifeng$ticking;
    @Unique private boolean zifeng$ticked;
    @Unique private int zifeng$cursor;
    @Unique private boolean zifeng$hasVirtual;
    @Unique private final ArrayList<CraftingCPUCluster> zifeng$order = new ArrayList<>();

    @Unique private void zifeng$refresh() {
        if (Ae2Effects.getCpuInfiniteLevel() == 0 && !zifeng$hasVirtual) return;
        VirtualCpus.refresh(craftingCPUClusters, (CraftingService) (Object) this);
        zifeng$hasVirtual = false;
        for (var cpu : craftingCPUClusters) {
            if (VirtualCpus.state(cpu).parent != null) { zifeng$hasVirtual = true; break; }
        }
    }
    @Inject(method = "updateCPUClusters", at = @At("TAIL"), require = 1)
    private void zifeng$restoreSlots(CallbackInfo ci) {
        for (var cpu : craftingCPUClusters) {
            if (!VirtualCpus.state(cpu).children.isEmpty()) { zifeng$hasVirtual = true; break; }
        }
        zifeng$refresh();
    }
    @Inject(method = {"getCpus", "submitJob"}, at = @At("HEAD"), require = 1)
    private void zifeng$allocateSlots(CallbackInfoReturnable<?> ci) {
        if (!zifeng$ticking) zifeng$refresh();
    }

    /**
     * 第三方 CPU 的<b>保守</b>适配（1.1.5 起）。
     * <p>1.1.4 曾把集合里每个非 {@link CraftingCPUCluster} 元素包成装饰器，结果与 Neo Eco AE
     * Extension 冲突：ECO 的 {@code hasCpu} 用严格引用相等（{@code activeCpu == cpu} /
     * {@code getFakeCPU() == cpu}）判断归属，而本类每次 new 出的陌生装饰器永远不匹配，
     * 导致 CPU 列表点击无反应、合成状态不显示。
     * <p>现在改为：只要集合里出现任何外来 CPU，就<b>整体退出、原样返回</b>（见
     * {@link CpuAdapterRegistry#wrapAll}）。集合里全是原生 CPU 时也原样返回——那条路已由
     * {@code CraftingCpuSpeedMixin} 在数值层处理，无需在此重建引用。
     * <p>该注入因此不再修改返回值，保留它是为了在「检测到第三方自有 CPU」时留下可诊断的日志。
     */
    @Inject(method = "getCpus", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$adaptThirdPartyCpus(CallbackInfoReturnable<ImmutableSet<ICraftingCPU>> cir) {
        var nativeCpus = cir.getReturnValue();
        var result = CpuAdapterRegistry.wrapAll(nativeCpus, cpus -> {
            // 保守模式下永远走不到这里；保留实现是为了将来若确定某类 CPU 可安全放大时可直接启用。
            var rebuilt = ImmutableSet.<ICraftingCPU>builderWithExpectedSize(cpus.size());
            for (var cpu : cpus) rebuilt.add(cpu);
            return rebuilt.build();
        });
        if (result != nativeCpus) cir.setReturnValue(result);
    }
    @Inject(method = "onServerEndTick", at = @At("HEAD"), require = 1)
    private void zifeng$beginTick(CallbackInfo ci) {
        zifeng$refresh();
        zifeng$ticking = true;
        zifeng$ticked = false;
    }
    @Inject(method = "onServerEndTick", at = @At("RETURN"), require = 1)
    private void zifeng$endTick(CallbackInfo ci) { zifeng$ticking = false; }

    // Run the native logic once each in rotating order, before the native change aggregation.
    // This prevents the first CPU in a HashSet from monopolizing the instant-crafting time budget.
    @Redirect(method = "onServerEndTick", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;Lappeng/me/service/CraftingService;)V"), require = 1)
    private void zifeng$fairTick(CraftingCpuLogic ignored, IEnergyService energy, CraftingService service) {
        if (Ae2Effects.getAssemblerInfiniteLevel() == 0 && Ae2Effects.getCpuInfiniteLevel() < 5) {
            ignored.tickCraftingLogic(energy, service);
            return;
        }
        if (zifeng$ticked) return;
        zifeng$ticked = true;
        var order = zifeng$order;
        if (order.size() != craftingCPUClusters.size() || !craftingCPUClusters.containsAll(order)) {
            order.clear();
            order.addAll(craftingCPUClusters);
            order.sort(Comparator.comparingInt(System::identityHashCode));
        }
        if (order.isEmpty()) return;
        int first = Math.floorMod(zifeng$cursor++, order.size());
        for (int i = 0; i < order.size(); i++) {
            order.get((first + i) % order.size()).craftingLogic.tickCraftingLogic(energy, service);
        }
    }

    @Redirect(method = "insertIntoCpus", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"), require = 1)
    private long zifeng$ownFinalOutput(CraftingCpuLogic logic, AEKey key, long amount, Actionable mode) {
        // Completion is already accounted to the originating CPU. Do not let a standalone
        // sibling also mark its final order complete using these same items (native insert returns 0).
        if (InstantCrafting.isDeliveringFinalOutput() && key.matches(logic.getFinalJobOutput())) return 0;
        return logic.insert(key, amount, mode);
    }
}
