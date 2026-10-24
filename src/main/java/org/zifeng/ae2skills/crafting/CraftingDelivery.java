package org.zifeng.ae2skills.crafting;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/**
 * 产物投递的<b>唯一</b>实现：先交给合成任务（{@link CpuExecution#skillInsert}），
 * 无人领取时落进 ME 存储，最后兜底塞回 CPU 内部库存。
 *
 * <p>从 {@link InstantCrafting} 抽出（D6 复用），因为「无尽装配」与「万机之神的加护 6 级瞬成」
 * 必须共用同一套三级兜底——两处各写一份迟早会分叉，而分叉的表现是「产物凭空消失」。
 */
public final class CraftingDelivery {
    /** 正在由本模组投递产物的层数；嵌套投递时用来避免重入判定互相污染。 */
    private static int depth;
    /** 只在「最终产物」投递期间的层数，供 AE2 侧的兄弟 CPU 去重使用。 */
    private static int finalDepth;

    private CraftingDelivery() {}

    /** 原始语义：此刻是否正在投递计为「最终产物」的堆。见 AeCraftingServiceMixin。 */
    public static boolean isDeliveringFinalOutput() { return finalDepth > 0; }

    /**
     * 此刻是否有本模组的产物投递正在进行。
     * <p>「万机之神的加护」用它把<b>自己投递</b>造成的 {@code insert} 排除在验证证据之外：
     * 否则同网络上另一条「无尽装配」投递的、恰好同名的产物，会把一个根本没被机器验证过的
     * 样板误判成「已成功」，进而凭空结清剩余批次。
     */
    public static boolean isDelivering() { return depth > 0; }

    public static void deliver(CpuExecution logic, AEKey key, long amount) {
        if (amount <= 0) return;
        boolean isFinal = key.matches(logic.skillFinalOutput());
        depth++;
        try {
            long remaining = amount - logic.skillInsert(key, amount, Actionable.MODULATE);
            // A standalone terminal order has no requester: native insert returns 0 even after
            // accepting the completion. Its output belongs in ME storage, not in a failed-job branch.
            if (remaining > 0 && logic.skillGrid() != null) {
                if (isFinal) finalDepth++;
                try {
                    remaining -= logic.skillGrid().getStorageService().getInventory()
                            .insert(key, remaining, Actionable.MODULATE, logic.skillSource());
                } finally {
                    if (isFinal) finalDepth--;
                }
            }
            // Full storage must keep the output, including after the native job has already completed.
            if (remaining > 0) logic.skillInventory().insert(key, remaining, Actionable.MODULATE);
        } finally {
            depth--;
        }
    }
}
