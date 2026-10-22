package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface CraftingTimeAccess extends org.zifeng.ae2skills.crafting.TimeAccess {
    @Invoker("addMaxItems") void zifeng$addMaxItems(long amount, AEKeyType type);
}
