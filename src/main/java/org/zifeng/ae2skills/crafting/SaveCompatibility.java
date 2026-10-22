package org.zifeng.ae2skills.crafting;

import java.util.HashSet;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/** Validate the entire tree before the native reader can silently skip unknown items or patterns. */
public final class SaveCompatibility {
    private SaveCompatibility() {}
    public static void validate(Level level, CompoundTag tag, HolderLookup.Provider registries) {
        validateCpu(level, tag, registries);
        var children = list(tag, "zifeng_virtual_cpus");
        var slots = new HashSet<Integer>();
        for (int i = 0; i < children.size(); i++) {
            var child = children.getCompound(i);
            int slot = child.getInt("slot");
            require(slot > 0 && slots.add(slot), "Invalid or duplicate virtual CPU slot " + slot);
            require(!child.contains("zifeng_virtual_cpus"), "Nested virtual CPUs are not supported");
            validateCpu(level, child, registries);
        }
    }
    private static void validateCpu(Level level, CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("zifeng_reserved_bytes")) require(tag.contains("zifeng_reserved_bytes", Tag.TAG_ANY_NUMERIC)
                && tag.getLong("zifeng_reserved_bytes") >= 0, "Invalid saved capacity reservation");
        inventory(list(tag, "inventory"), registries);
        if (!tag.contains("job")) return;
        require(tag.contains("job", Tag.TAG_COMPOUND), "Invalid job type");
        var job = tag.getCompound("job");
        var output = GenericStack.readTag(registries, job.getCompound("finalOutput"));
        require(output != null && output.amount() > 0, "Unknown final output");
        require(job.getLong("remainingAmount") >= 0, "Invalid remaining output count");
        require(job.getCompound("link").hasUUID("craftId"), "Missing crafting link UUID");
        inventory(list(job, "waitingFor"), registries);
        var tasks = list(job, "tasks");
        for (int i = 0; i < tasks.size(); i++) {
            var task = tasks.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, task);
            require(pattern != null && PatternDetailsHelper.decodePattern(pattern, level) != null, "Unknown or incompatible pattern in task " + i);
            require(task.getLong("#craftingProgress") >= 0, "Invalid task progress");
        }
    }
    private static ListTag list(CompoundTag tag, String key) {
        if (!tag.contains(key)) return new ListTag();
        require(tag.contains(key, Tag.TAG_LIST), "Invalid list: " + key);
        ListTag list = (ListTag) tag.get(key);
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, "Invalid entries: " + key);
        return list;
    }
    private static void inventory(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            require(AEKey.fromTagGeneric(registries, entry) != null, "Unknown AE2 inventory key at " + i);
            require(entry.getLong("#") >= 0, "Negative inventory count at " + i);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
