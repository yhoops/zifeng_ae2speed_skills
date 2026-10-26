package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface CraftingJobAccess extends org.zifeng.ae2skills.crafting.JobAccess {
    @Accessor("tasks") Map<IPatternDetails, Object> zifeng$getTasks();
    @Accessor("waitingFor") ListCraftingInventory zifeng$getWaitingFor();
    @Accessor("link") CraftingLink zifeng$getLink();
}
