package org.zifeng.ae2skills.compat;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 「使用 AE 能量」功能的启用状态。
 *
 * <p>与四个 AE 技能不同，本功能默认关闭、不持有存档数据，契约不匹配时唯一的后果是退回 FE 供电。
 * 因此它单独容错：契约检查失败只禁用本功能，附属其余功能照常加载。
 */
public final class AePowerFeature {
    private static final Set<String> PROBLEMS = new LinkedHashSet<>();
    private AePowerFeature() {}
    static void problem(String text) { PROBLEMS.add(text); }
    public static boolean enabled() { return PROBLEMS.isEmpty(); }
    public static String diagnostic() {
        return "AE 供电功能已禁用（本体契约不匹配）/ AE power disabled (host contract mismatch): "
                + String.join("; ", PROBLEMS);
    }
}
