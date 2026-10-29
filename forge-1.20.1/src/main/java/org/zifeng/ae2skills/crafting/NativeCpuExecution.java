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

/**
 * AE2 implementation of the shared execution contract.
 * <p>1.2.1 起主配置注入失败只降级不崩，因此访问器混入有可能没挂上。所有访问器/调用器
 * 都经由 {@link #access()} 之类的检测入口，缺失时返回原版语义的零值，而不是抛
 * {@code ClassCastException} 打断服务器 tick。
 */
public record NativeCpuExecution(CraftingCpuLogic logic) implements CpuExecution {
    private CpuLogicAccess access() { return logic instanceof CpuLogicAccess a ? a : null; }

    /** 集群；访问器没挂上时为 null。 */
    private appeng.me.cluster.implementations.CraftingCPUCluster cluster() {
        var access = access();
        return access == null ? null : access.zifeng$getCluster();
    }

    public Object skillJob() { var a = access(); return a == null ? null : a.zifeng$getJob(); }
    public ListCraftingInventory skillInventory() { return logic.getInventory(); }

    public Level skillLevel() {
        var cluster = cluster();
        return cluster == null ? null : cluster.getLevel();
    }

    public IGrid skillGrid() {
        var cluster = cluster();
        return cluster == null ? null : cluster.getGrid();
    }

    public IActionSource skillSource() {
        var cluster = cluster();
        return cluster == null ? null : cluster.getSrc();
    }

    public boolean skillActive() {
        var cluster = cluster();
        return cluster != null && cluster.isActive();
    }

    // 1.20.1：CraftingCpuLogic 没有 isJobSuspended()（暂停任务是 1.21 新增），恒为 false 即等价
    public boolean skillSuspended() { return false; }
    public GenericStack skillFinalOutput() { return logic.getFinalJobOutput(); }
    public long skillInsert(AEKey key, long amount, Actionable mode) { return logic.insert(key, amount, mode); }

    public void skillMarkDirty() {
        var cluster = cluster();
        if (cluster != null) cluster.markDirty();
    }

    public void skillAddMaxItems(long amount, AEKeyType type) {
        if (logic.getElapsedTimeTracker() instanceof CraftingTimeAccess access) {
            access.zifeng$addMaxItems(amount, type);
        }
    }
}
