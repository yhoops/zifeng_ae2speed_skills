package org.zifeng.ae2skills.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface CraftingTaskAccess extends org.zifeng.ae2skills.crafting.TaskAccess {
    @Accessor("value") long zifeng$getRemaining();
    @Accessor("value") void zifeng$setRemaining(long value);
}
