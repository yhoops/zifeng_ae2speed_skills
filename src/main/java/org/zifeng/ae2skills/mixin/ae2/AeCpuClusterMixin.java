package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.crafting.CpuExtension;
import org.zifeng.ae2skills.crafting.VirtualCpus;
import org.zifeng.ae2skills.crafting.VirtualCpuState;

@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class AeCpuClusterMixin implements CpuExtension {
    @Unique private boolean zifeng$readingBase;
    @Unique private final VirtualCpuState zifeng$state = new VirtualCpuState();
    public VirtualCpuState zifeng$state() { return zifeng$state; }
    public boolean zifeng$readingBase() { return zifeng$readingBase; }
    public long zifeng$baseStorage() {
        boolean previous = zifeng$readingBase;
        zifeng$readingBase = true;
        try { return ((CraftingCPUCluster) (Object) this).getAvailableStorage(); }
        finally { zifeng$readingBase = previous; }
    }
    public int zifeng$baseCoProcessors() {
        boolean previous = zifeng$readingBase;
        zifeng$readingBase = true;
        try { return ((CraftingCPUCluster) (Object) this).getCoProcessors(); }
        finally { zifeng$readingBase = previous; }
    }

    @Inject(method = "getNode", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$node(CallbackInfoReturnable<IGridNode> ci) {
        if (zifeng$state.parent != null) ci.setReturnValue(zifeng$state.parent.getNode());
    }
    @Inject(method = "getSrc", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$source(CallbackInfoReturnable<IActionSource> ci) {
        if (zifeng$state.parent != null) ci.setReturnValue(zifeng$state.parent.getSrc());
    }
    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$level(CallbackInfoReturnable<Level> ci) {
        if (zifeng$state.parent != null) ci.setReturnValue(zifeng$state.parent.getLevel());
    }
    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$dirty(CallbackInfo ci) {
        if (zifeng$state.parent != null) { zifeng$state.parent.markDirty(); ci.cancel(); }
    }
    @Inject(method = "isDestroyed", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$destroyed(CallbackInfoReturnable<Boolean> ci) {
        if (zifeng$state.retired || (zifeng$state.parent != null && zifeng$state.parent.isDestroyed())) ci.setReturnValue(true);
    }
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$name(CallbackInfoReturnable<Component> ci) {
        var parent = zifeng$state.parent;
        if (parent == null) return;
        var name = parent.getName();
        var pos = parent.getBoundsMin();
        if (name == null) name = Component.translatable("ui.zifeng_s_custom_skill_tree.ae_cpu_at", pos.getX(), pos.getY(), pos.getZ());
        ci.setReturnValue(Component.translatable("ui.zifeng_s_custom_skill_tree.ae_virtual_cpu", name, zifeng$state.slot + 1));
    }
    @Inject(method = "writeToNBT", at = @At("TAIL"), require = 1)
    private void zifeng$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        VirtualCpus.write((CraftingCPUCluster) (Object) this, tag, registries);
    }
    @Inject(method = "readFromNBT", at = @At("TAIL"), require = 1)
    private void zifeng$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        VirtualCpus.read((CraftingCPUCluster) (Object) this, tag, registries);
    }
    @Inject(method = "readFromNBT", at = @At("HEAD"), require = 1)
    private void addon$validateSave(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        var cpu = (CraftingCPUCluster) (Object) this;
        try {
            org.zifeng.ae2skills.crafting.SaveCompatibility.validate(cpu.getLevel(), tag, registries);
        } catch (RuntimeException failure) {
            zifeng$state.failedRestore = tag.copy();
            var server = cpu.getLevel().getServer();
            if (server != null) server.halt(false);
            throw new IllegalStateException("AE2 技能任务无法恢复，已停止服务器并保留原始任务数据。请恢复兼容的 AE2、配方及模组后重试。 / "
                    + "Cannot restore AE2 skill jobs; stopping server and preserving original NBT. Restore compatible AE2, recipes and mods. CPU: "
                    + cpu.getBoundsMin() + "; " + failure.getMessage(), failure);
        }
    }
}
