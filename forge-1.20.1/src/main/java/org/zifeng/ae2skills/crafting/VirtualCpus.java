package org.zifeng.ae2skills.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.zifeng.ae2skills.Ae2Effects;

/** Real AE2 CPU instances with independent jobs and a shared physical capacity pool. */
public final class VirtualCpus {
    private static final String CHILDREN = "zifeng_virtual_cpus";
    private static final String BYTES = "zifeng_reserved_bytes";
    private VirtualCpus() {}

    public static VirtualCpuState state(CraftingCPUCluster cpu) { return ((CpuExtension) (Object) cpu).zifeng$state(); }

    /**
     * 算力超频／分裂算力对「并行处理单元数量」的唯一放大入口。
     * <p>原生 CPU 路径与第三方 CPU 通用适配器都走这里，保证同一台 CPU 不会因为
     * 走不同路径而算出两个不同数字。倍率为 0（默认）时返回 {@code base} 本身。
     */
    public static int scaleCoProcessors(int base) { return Ae2Effects.scaledCpuCoProcessors(base); }

    /**
     * 分裂算力对「合成存储容量」的唯一放大入口。
     * <p>语义与 {@link #scaleCoProcessors(int)} 相同：原生与第三方路径共用一处逻辑。
     */
    public static long scaleStorage(long base) { return Ae2Effects.scaledCpuStorage(base); }
    public static CraftingCPUCluster root(CraftingCPUCluster cpu) {
        var parent = state(cpu).parent;
        return parent == null ? cpu : parent;
    }
    public static boolean occupied(CraftingCPUCluster cpu) {
        return cpu.craftingLogic.hasJob() || !cpu.craftingLogic.getInventory().list.isEmpty();
    }
    private static long reserved(CraftingCPUCluster cpu) {
        return occupied(cpu) ? state(cpu).reservedBytes : 0;
    }
    public static long availableStorage(CraftingCPUCluster cpu) {
        var root = root(cpu);
        var data = state(cpu);
        if (data.retired || (data.slot >= Ae2Effects.getCpuSlotLimit() && !occupied(cpu))) return 0;
        long total = scaleStorage(((CpuExtension) (Object) root).zifeng$baseStorage());
        if (total == Long.MAX_VALUE) return Long.MAX_VALUE;
        long remaining = Math.max(0, total - reserved(root));
        for (var child : state(root).children) remaining = Math.max(0, remaining - reserved(child));
        return remaining;
    }
    public static int coProcessors(CraftingCPUCluster cpu) {
        int base = ((CpuExtension) (Object) root(cpu)).zifeng$baseCoProcessors();
        return scaleCoProcessors(base);
    }
    public static boolean canSubmit(CraftingCPUCluster cpu, IGrid grid) {
        var physical = root(cpu);
        var data = state(cpu);
        if (data.externallyManaged) return !cpu.isDestroyed() && cpu.getGrid() == grid && !occupied(cpu);
        if (data.retired || state(physical).breaking || data.slot >= Ae2Effects.getCpuSlotLimit()
                || cpu.isDestroyed() || cpu.getGrid() != grid || occupied(cpu)) return false;
        int active = physical.isBusy() ? 1 : 0;
        for (var child : state(physical).children) if (child.isBusy()) active++;
        return active < Ae2Effects.getCpuSlotLimit();
    }

    /** Called outside the native set iteration, never while a job is returning items. */
    public static void refresh(Set<CraftingCPUCluster> cpus, CraftingService service) {
        int limit = Ae2Effects.getCpuSlotLimit();
        var physical = new ArrayList<CraftingCPUCluster>();
        for (var cpu : cpus) if (!state(cpu).externallyManaged && state(cpu).parent == null && !cpu.isDestroyed()) physical.add(cpu);
        cpus.removeIf(cpu -> state(cpu).parent != null &&
                (state(cpu).retired || !cpus.contains(state(cpu).parent) || state(cpu).parent.isDestroyed()));
        for (var parent : physical) {
            var data = state(parent);
            boolean spare = !occupied(parent);
            var iterator = data.children.iterator();
            while (iterator.hasNext()) {
                var child = iterator.next();
                boolean idle = !occupied(child);
                boolean remove = idle && (state(child).slot >= limit || (limit == Integer.MAX_VALUE && spare));
                if (remove) {
                    state(child).retired = true;
                    cpus.remove(child);
                    iterator.remove();
                    parent.markDirty();
                } else if (idle) spare = true;
            }
            if (limit == Integer.MAX_VALUE) {
                if (!spare) {
                    var used = new HashSet<Integer>();
                    for (var child : data.children) used.add(state(child).slot);
                    int slot = 1;
                    while (used.contains(slot)) slot++;
                    data.children.add(create(parent, slot));
                    parent.markDirty();
                }
            } else {
                for (int slot = 1; slot < limit; slot++) {
                    boolean found = false;
                    for (var child : data.children) if (state(child).slot == slot) { found = true; break; }
                    if (!found) { data.children.add(create(parent, slot)); parent.markDirty(); }
                }
            }
            for (var child : data.children) {
                if (cpus.add(child) && child.craftingLogic.getLastLink() instanceof CraftingLink link) service.addLink(link);
            }
        }
    }

    private static CraftingCPUCluster create(CraftingCPUCluster parent, int slot) {
        var child = new CraftingCPUCluster(parent.getBoundsMin(), parent.getBoundsMax());
        state(child).parent = parent;
        state(child).slot = slot;
        child.getConfigManager().putSetting(Settings.CPU_SELECTION_MODE, parent.getSelectionMode());
        return child;
    }
    // 1.20.1：CraftingCPUCluster.writeToNBT/readFromNBT 只收 (CompoundTag)V，没有 HolderLookup.Provider
    public static void write(CraftingCPUCluster cpu, CompoundTag tag) {
        if (state(cpu).failedRestore != null) { tag.merge(state(cpu).failedRestore.copy()); return; }
        tag.putLong(BYTES, reserved(cpu));
        var data = state(cpu);
        if (data.parent != null) return;
        var children = new ListTag();
        for (var child : data.children) {
            var entry = new CompoundTag();
            entry.putInt("slot", state(child).slot);
            child.writeToNBT(entry);
            children.add(entry);
        }
        tag.put(CHILDREN, children);
    }
    public static void read(CraftingCPUCluster cpu, CompoundTag tag) {
        var data = state(cpu);
        data.failedRestore = null;
        // Legacy jobs do not record plan.bytes(). Reserve the pool until they drain.
        data.reservedBytes = tag.contains(BYTES) ? Math.max(0, tag.getLong(BYTES))
                : occupied(cpu) ? Long.MAX_VALUE : 0;
        if (data.parent != null) return;
        for (var old : data.children) state(old).retired = true;
        data.children.clear();
        var children = tag.getList(CHILDREN, 10);
        var used = new HashSet<Integer>();
        for (int i = 0; i < children.size(); i++) {
            var entry = children.getCompound(i);
            int slot = entry.getInt("slot");
            if (slot < 1 || !used.add(slot)) throw new IllegalStateException("Invalid saved virtual CPU slot: " + slot);
            var child = create(cpu, slot);
            data.children.add(child);
            child.readFromNBT(entry);
        }
    }
    /** Called before the physical BE's native cancel/refund/drop path. */
    public static void beforeBreak(CraftingCPUCluster parent) {
        var data = state(parent);
        if (data.parent != null || data.breaking) return;
        data.breaking = true;
        for (var child : data.children) {
            child.cancelJob();
            var inventory = child.craftingLogic.getInventory();
            for (var entry : inventory.list) {
                parent.craftingLogic.getInventory().insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
            inventory.clear();
            state(child).retired = true;
        }
        data.children.clear();
    }
}
