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
    /** 「神明之力」页签的唯一技能：AE 任务激活时给整条产线动态加速。 */
    public static final String DIVINE_MACHINE = "ae_divine_machine_grace";
    public static final List<String> IDS = List.of(ASSEMBLER_SPEED, CPU_SPEED, ASSEMBLER_INFINITE, CPU_INFINITE, DIVINE_MACHINE);

    /** 宿主本来就有的 11 个页签；我们追加第 12 个「神明之力」。 */
    public static final int HOST_CATEGORY_COUNT = 11;
    public static final int DIVINE_CATEGORY_INDEX = 11;
    public static final int CATEGORY_COUNT = HOST_CATEGORY_COUNT + 1;
    /** 宿主 CATEGORY_TITLE_KEYS 里追加的键；完整键为 ui.zifeng_s_custom_skill_tree.col_divine。 */
    public static final String DIVINE_CATEGORY_KEY = "col_divine";
    /** 页签强调色（ARGB，与宿主 CATEGORY_COLORS 同格式）。 */
    public static final int DIVINE_CATEGORY_COLOR = 0xFFB388FF;

    /** 五级倍率：索引即等级。1→×2 / 2→×8 / 3→×16 / 4→×128 / 5→×1024。 */
    private static final int[] DIVINE_MULTIPLIERS = {1, 2, 8, 16, 128, 1024};
    /** 非等比费用，不能套用 base<<n。 */
    private static final long[] DIVINE_COSTS = {1000L, 5000L, 10000L, 50000L, 100000L};

    private SkillDefinitions() {}

    // 宿主会在 tooltip 缓存字段还是 null 时也调用这些判定，因此必须容忍 null
    // （List.of(...).contains(null) 会抛 NPE，不能直接透传）。
    public static boolean contains(String id) { return id != null && IDS.contains(id); }
    /**
     * 「万机之神」专用：决定 {@link #cost} 是否走神明费用表。
     * 不要把它扩成三个技能，否则「合成之神 / 算力之神」的升级消耗会被整体改写。
     */
    public static boolean isDivine(String id) { return DIVINE_MACHINE.equals(id); }
    /**
     * 「神明之力」页签的成员表：万机之神的加护、合成之神的加护、算力之神的加护。
     * 不可变常量：宿主 UI 每次搜索/点击都会枚举全部页签，别在热路径上反复分配。
     */
    public static final List<String> DIVINE_TAB_SKILLS = List.of(DIVINE_MACHINE, ASSEMBLER_INFINITE, CPU_INFINITE);
    /** 页签成员数量；宿主 {@code GLOBAL_SKILLS} 过滤后的长度校验要用它。由成员表派生，避免两处漂移。 */
    public static final int DIVINE_TAB_COUNT = DIVINE_TAB_SKILLS.size();
    public static List<String> divineSkills() { return DIVINE_TAB_SKILLS; }
    /** 是否归属「神明之力」页签（只影响页签归属，不影响费用与效果）。 */
    public static boolean inDivineTab(String id) { return id != null && DIVINE_TAB_SKILLS.contains(id); }

    /** 成就 ID 后缀，与 data/zifeng_ae2_skills/advancement/&lt;后缀&gt;.json 一一对应。 */
    public static final String ACH_MACHINE = "divine_machine_grace";
    public static final String ACH_ASSEMBLER = "divine_assembler_grace";
    public static final String ACH_CPU = "divine_cpu_grace";
    /** 该技能满级时对应的成就后缀；没有成就的技能返回 {@code null}。 */
    public static String achievementPath(String id) {
        return switch (id) {
            case DIVINE_MACHINE -> ACH_MACHINE;
            case ASSEMBLER_INFINITE -> ACH_ASSEMBLER;
            case CPU_INFINITE -> ACH_CPU;
            default -> null;
        };
    }
    public static int maxLevel(String id) { return id.equals(ASSEMBLER_SPEED) || id.equals(CPU_SPEED) ? 4 : 5; }
    public static int divineMultiplier(int level) { return DIVINE_MULTIPLIERS[Math.clamp(level, 0, DIVINE_MULTIPLIERS.length - 1)]; }
    public static long cost(String id, int current) {
        if (isDivine(id)) return DIVINE_COSTS[Math.clamp(current, 0, DIVINE_COSTS.length - 1)];
        long base = id.equals(ASSEMBLER_SPEED) ? 500 : id.equals(CPU_SPEED) ? 1000 : 2000;
        return base << Math.clamp(current, 0, maxLevel(id) - 1);
    }
    public static Item icon(String id) {
        return switch (id) {
            case ASSEMBLER_SPEED -> Items.CRAFTER;
            case CPU_SPEED -> Items.COMPARATOR;
            case ASSEMBLER_INFINITE -> Items.END_CRYSTAL;
            case DIVINE_MACHINE -> Items.NETHER_STAR;
            default -> Items.AMETHYST_CLUSTER;
        };
    }
}
