package org.zifeng.ae2skills.client;

import net.minecraft.network.chat.Component;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.ae2skills.StatePayload;

/** Contains no client-only Minecraft types, safe as a network handler target on a dedicated server. */
public final class ClientEffects {
    private static StatePayload state = new StatePayload(0, 0, 0, 0, 0);
    private static int revision;
    private ClientEffects() {}
    public static void apply(StatePayload next) { state = next; revision++; }
    public static int revision() { return revision; }
    public static int level(String id) {
        return switch (id) {
            case SkillDefinitions.ASSEMBLER_SPEED -> state.assembler();
            case SkillDefinitions.CPU_SPEED -> state.cpu();
            case SkillDefinitions.ASSEMBLER_INFINITE -> state.instant();
            case SkillDefinitions.CPU_INFINITE -> state.split();
            case SkillDefinitions.DIVINE_MACHINE -> state.divine();
            default -> 0;
        };
    }
    public static String value(String id, int level) {
        level = Math.clamp(level, 0, SkillDefinitions.maxLevel(id));
        if (level == 0) return Component.translatable("ui.zifeng_ae2_skills.disabled").getString();
        if (id.equals(SkillDefinitions.DIVINE_MACHINE)) return "×" + SkillDefinitions.divineMultiplier(level);
        if (id.equals(SkillDefinitions.ASSEMBLER_SPEED) || id.equals(SkillDefinitions.CPU_SPEED)) return "×" + (level + 1);
        if (level == 5) return "∞";
        return Component.translatable(id.equals(SkillDefinitions.CPU_INFINITE)
                ? "ui.zifeng_ae2_skills.cpus" : "ui.zifeng_ae2_skills.ops", 1 << level).getString();
    }
}
