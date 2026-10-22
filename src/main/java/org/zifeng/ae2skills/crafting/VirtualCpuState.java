package org.zifeng.ae2skills.crafting;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.ArrayList;
import java.util.List;

/** Owned by its cluster, so worlds and disconnected grids do not leak through static maps. */
public final class VirtualCpuState {
    public CraftingCPUCluster parent;
    public final List<CraftingCPUCluster> children = new ArrayList<>();
    public int slot;
    public long reservedBytes;
    public boolean retired;
    public boolean breaking;
    /** Another mod owns this task slot and its capacity/slot accounting. */
    public boolean externallyManaged;
    public net.minecraft.nbt.CompoundTag failedRestore;
}
