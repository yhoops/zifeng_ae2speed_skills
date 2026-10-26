package org.zifeng.ae2skills.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.world.level.Level;

/** Runtime adapter only; task creation, cancellation and persistence stay with the owning mod. */
public interface CpuExecution {
    Object skillJob();
    ListCraftingInventory skillInventory();
    Level skillLevel();
    IGrid skillGrid();
    IActionSource skillSource();
    boolean skillActive();
    boolean skillSuspended();
    GenericStack skillFinalOutput();
    long skillInsert(AEKey key, long amount, Actionable mode);
    void skillMarkDirty();
    void skillAddMaxItems(long amount, AEKeyType type);

    default long skillStored(AEKey key) { return skillInventory().list.get(key); }
}
