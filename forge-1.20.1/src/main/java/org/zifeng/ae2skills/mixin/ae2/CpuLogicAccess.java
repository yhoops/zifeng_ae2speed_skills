package org.zifeng.ae2skills.mixin.ae2;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public interface CpuLogicAccess {
    @Accessor("job") ExecutingCraftingJob zifeng$getJob();
    @Accessor("cluster") CraftingCPUCluster zifeng$getCluster();
}
