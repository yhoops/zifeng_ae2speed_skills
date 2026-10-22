package org.zifeng.ae2skills;

/** Shared budget for instant recipes and unlimited native dispatch, renewed each server tick. */
public final class TickBudget {
    private static long start, spent;
    private TickBudget() {}
    public static void markTickStart() { start = System.nanoTime(); spent = 0; }
    public static long tickStartNanos() { return start; }
    public static void reset() { start = spent = 0; }
    public static long deadline(long now) {
        long allowance = Math.min(1_000_000L, 20_000_000L - spent);
        if (start != 0) allowance = Math.min(allowance, start + 45_000_000L - now);
        return now + Math.max(0, allowance);
    }
    public static void charge(long began) { spent += Math.max(0, System.nanoTime() - began); }
}
