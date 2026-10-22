package org.zifeng.ae2skills.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import net.minecraft.world.level.Level;
import org.zifeng.ae2skills.mixin.ae2.CpuLogicAccess;
import org.zifeng.ae2skills.mixin.ae2.CraftingTimeAccess;

/** AE2 implementation of the shared execution contract. */
public record NativeCpuExecution(CraftingCpuLogic logic) implements CpuExecution {
    private CpuLogicAccess access() { return (CpuLogicAccess) logic; }
    public Object skillJob() { return access().zifeng$getJob(); }
    public ListCraftingInventory skillInventory() { return logic.getInventory(); }
    public Level skillLevel() { return access().zifeng$getCluster().getLevel(); }
    public IGrid skillGrid() { return access().zifeng$getCluster().getGrid(); }
    public IActionSource skillSource() { return access().zifeng$getCluster().getSrc(); }
    public boolean skillActive() { return access().zifeng$getCluster().isActive(); }
    public boolean skillSuspended() { return logic.isJobSuspended(); }
    public GenericStack skillFinalOutput() { return logic.getFinalJobOutput(); }
    public long skillInsert(AEKey key, long amount, Actionable mode) { return logic.insert(key, amount, mode); }
    public void skillMarkDirty() { access().zifeng$getCluster().markDirty(); }
    public void skillAddMaxItems(long amount, AEKeyType type) {
        ((CraftingTimeAccess) logic.getElapsedTimeTracker()).zifeng$addMaxItems(amount, type);
    }
}
