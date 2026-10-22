package org.zifeng.ae2skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned maxima; execution hooks only read the published primitive fields. */
public final class Ae2Effects {
    private static final Map<UUID, int[]> PLAYERS = new HashMap<>();
    private static final int[] LEVELS = new int[4];
    private static volatile int assemblerSpeed, cpuSpeed, assemblerInfinite, cpuInfinite;
    static boolean dirty;
    private Ae2Effects() {}
    public static double getAssemblerSpeedMultiplier() { return 1 + assemblerSpeed; }
    public static double getCpuSpeedMultiplier() { return 1 + cpuSpeed; }
    public static int getAssemblerSpeedLevel() { return assemblerSpeed; }
    public static int getCpuSpeedLevel() { return cpuSpeed; }
    public static int getAssemblerInfiniteLevel() { return assemblerInfinite; }
    public static int getCpuInfiniteLevel() { return cpuInfinite; }
    public static int getInstantOpsPerTick() { return assemblerInfinite == 0 ? 0 : assemblerInfinite == 5 ? Integer.MAX_VALUE : 1 << assemblerInfinite; }
    public static int getCpuSlotLimit() { return cpuInfinite == 5 ? Integer.MAX_VALUE : 1 << cpuInfinite; }
    public static int getCpuParallelCap() { return 4096; }
    /** Apply the overclock cap only to the bonus; never downgrade a third-party CPU's native performance. */
    public static int scaledCpuCoProcessors(int base) {
        if (cpuInfinite == 5) return Integer.MAX_VALUE - 1;
        if (cpuSpeed == 0) return base;
        long scaled = (base + 1L) * (1L + cpuSpeed) - 1;
        return (int) Math.max(base, Math.min(getCpuParallelCap(), Math.max(0, scaled)));
    }
    public static long scaledCpuStorage(long base) {
        if (cpuInfinite == 5) return Long.MAX_VALUE;
        int factor = 1 << cpuInfinite;
        return base > Long.MAX_VALUE / factor ? Long.MAX_VALUE : base * factor;
    }
    public static int[] snapshot() { return LEVELS.clone(); }
    public static void update(UUID player, int a, int c, int ai, int ci) {
        a = Math.clamp(a, 0, 4); c = Math.clamp(c, 0, 4);
        ai = Math.clamp(ai, 0, 5); ci = Math.clamp(ci, 0, 5);
        int[] old = PLAYERS.get(player);
        if (a == 0 && c == 0 && ai == 0 && ci == 0) { remove(player); return; }
        if (old != null && old[0] == a && old[1] == c && old[2] == ai && old[3] == ci) return;
        PLAYERS.put(player, new int[]{a, c, ai, ci});
        refresh();
    }
    public static void remove(UUID player) { if (PLAYERS.remove(player) != null) refresh(); }
    public static void clear() { PLAYERS.clear(); refresh(); }
    private static void refresh() {
        int a = 0, c = 0, ai = 0, ci = 0;
        for (int[] levels : PLAYERS.values()) {
            a = Math.max(a, levels[0]); c = Math.max(c, levels[1]);
            ai = Math.max(ai, levels[2]); ci = Math.max(ci, levels[3]);
        }
        if (a == assemblerSpeed && c == cpuSpeed && ai == assemblerInfinite && ci == cpuInfinite) return;
        assemblerSpeed = LEVELS[0] = a; cpuSpeed = LEVELS[1] = c;
        assemblerInfinite = LEVELS[2] = ai; cpuInfinite = LEVELS[3] = ci;
        dirty = true;
    }
    // Useful to the migration/GameTest harness; both paths use the same published state.
    public static void enableMachineSpeeds(UUID player, int a, int c) {
        int[] old = PLAYERS.getOrDefault(player, new int[4]); update(player, a, c, old[2], old[3]);
    }
    public static void enableMachineInfinites(UUID player, int a, int c) {
        int[] old = PLAYERS.getOrDefault(player, new int[4]); update(player, old[0], old[1], a, c);
    }
}
