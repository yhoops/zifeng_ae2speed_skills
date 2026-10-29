package org.zifeng.ae2skills.mixin.compat;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.crafting.VirtualCpus;

/** UFO retains its native pool/slot/NBT ownership; only effective reads are scaled. */
@Pseudo
@Mixin(targets = "com.raishxn.ufo.crafting.NexusSharedCraftingCpuPool", remap = false)
public abstract class UfoCpuPoolSkillsMixin {
    @Shadow private long totalStorage;
    @Shadow private int sharedCoProcessors;
    @Shadow private boolean infiniteMode;
    @Shadow protected abstract void recalculateRemaining();

    @Inject(method = {"getAvailableStorage", "submitJob"}, at = @At("HEAD"), require = 2)
    private void zifeng$refreshCapacity(CallbackInfoReturnable<?> cir) {
        recalculateRemaining();
    }
    @Redirect(method = "recalculateRemaining", at = @At(value = "FIELD",
            target = "Lcom/raishxn/ufo/crafting/NexusSharedCraftingCpuPool;totalStorage:J"), require = 1)
    private long zifeng$capacity(UfoCpuPoolSkillsMixin pool) {
        return Ae2Effects.scaledCpuStorage(totalStorage);
    }
    @Redirect(method = {"recalculateRemaining", "submitJob"}, at = @At(value = "FIELD",
            target = "Lcom/raishxn/ufo/crafting/NexusSharedCraftingCpuPool;infiniteMode:Z"), require = 3)
    private boolean zifeng$infinite(UfoCpuPoolSkillsMixin pool) {
        return infiniteMode || Ae2Effects.getCpuInfiniteLevel() >= 5;
    }
    @Redirect(method = {"getCoProcessors", "tickCraftingLogic"}, at = @At(value = "FIELD",
            target = "Lcom/raishxn/ufo/crafting/NexusSharedCraftingCpuPool;sharedCoProcessors:I"), require = 3)
    private int zifeng$processors(UfoCpuPoolSkillsMixin pool) {
        return Ae2Effects.scaledCpuCoProcessors(sharedCoProcessors);
    }
    @WrapOperation(method = {"submitJob", "readFromNBT"}, at = @At(value = "NEW",
            target = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lappeng/me/cluster/implementations/CraftingCPUCluster;"), require = 2)
    private CraftingCPUCluster zifeng$ownedSlot(BlockPos min, BlockPos max, Operation<CraftingCPUCluster> original) {
        var cpu = original.call(min, max);
        // 半应用状态下 state() 返回共享占位，写入会泄漏到所有 CPU，因此走可写入口。
        var state = VirtualCpus.mutableState(cpu);
        if (state != null) state.externallyManaged = true;
        return cpu;
    }
}
