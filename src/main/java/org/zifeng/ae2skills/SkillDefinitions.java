package org.zifeng.ae2skills;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** IDs are a saved-data contract, including the original unnamespaced spelling. */
public final class SkillDefinitions {
    public static final String ASSEMBLER_SPEED = "ae_assembler_speed";
    public static final String CPU_SPEED = "ae_cpu_speed";
    public static final String ASSEMBLER_INFINITE = "ae_assembler_infinite";
    public static final String CPU_INFINITE = "ae_cpu_infinite";
    public static final List<String> IDS = List.of(ASSEMBLER_SPEED, CPU_SPEED, ASSEMBLER_INFINITE, CPU_INFINITE);
    private SkillDefinitions() {}
    public static boolean contains(String id) { return IDS.contains(id); }
    public static int maxLevel(String id) { return id.equals(ASSEMBLER_SPEED) || id.equals(CPU_SPEED) ? 4 : 5; }
    public static long cost(String id, int current) {
        long base = id.equals(ASSEMBLER_SPEED) ? 500 : id.equals(CPU_SPEED) ? 1000 : 2000;
        return base << Math.clamp(current, 0, maxLevel(id) - 1);
    }
    public static Item icon(String id) {
        return switch (id) {
            case ASSEMBLER_SPEED -> Items.CRAFTER;
            case CPU_SPEED -> Items.COMPARATOR;
            case ASSEMBLER_INFINITE -> Items.END_CRYSTAL;
            default -> Items.AMETHYST_CLUSTER;
        };
    }
}
