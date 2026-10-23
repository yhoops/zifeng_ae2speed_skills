package org.zifeng.ae2skills;

/** Shared budget for instant recipes, unlimited native dispatch and divine machine acceleration, renewed each server tick. */
public final class TickBudget {
    /** Hard ceiling for one shared work slice inside a single server tick. */
    private static final long HARD_SPENT_LIMIT = 20_000_000L;
    /** Never spend past this point of the tick, even when the budget still has room. */
    private static final long TICK_SPEND_WINDOW = 45_000_000L;
    private static long start, spent;
    private TickBudget() {}
    public static void markTickStart() { start = System.nanoTime(); spent = 0; }
    public static long tickStartNanos() { return start; }
    public static void reset() { start = spent = 0; }
    public static long deadline(long now) { return deadline(now, 1_000_000L); }
    /** Same shared pool, but with a larger slice for callers that were explicitly tuned for it. */
    public static long deadline(long now, long allowanceNanos) {
        long allowance = Math.min(allowanceNanos, HARD_SPENT_LIMIT - spent);
        if (start != 0) allowance = Math.min(allowance, start + TICK_SPEND_WINDOW - now);
        return now + Math.max(0, allowance);
    }
    public static void charge(long began) { spent += Math.max(0, System.nanoTime() - began); }
}
