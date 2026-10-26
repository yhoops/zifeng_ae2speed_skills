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

    /**
     * 宿主页签数量：**运行时从宿主读取**，不在本附属里写死。
     * <p>宿主 1.20.1 的 {@code SkillTreeScreen.CATEGORY_COUNT} 带 ConstantValue 属性，
     * 反射可读；本体将来新增页签时这里自动跟随，不需要我们改代码。
     * <p>用反射而不是直接引用 {@code SkillTreeScreen} 类字面量，是因为本类同时被服务端逻辑使用，
     * 直接引用客户端屏幕类会让专用服务器在加载期就失败。
     */
    public static int hostCategoryCount() {
        Integer cached = HOST_CATEGORY_COUNT_CACHE;
        if (cached != null) return cached;
        int resolved = resolveHostCategoryCount();
        HOST_CATEGORY_COUNT_CACHE = resolved;
        return resolved;
    }

    /** 反射失败时的兜底：1.20.1 宿主的页签数。仅在本体结构彻底变化时才会用到。 */
    private static final int HOST_CATEGORY_COUNT_FALLBACK = 12;
    private static volatile Integer HOST_CATEGORY_COUNT_CACHE;

    private static int resolveHostCategoryCount() {
        try {
            Class<?> screen = Class.forName("org.zifeng.skilltree.client.screen.SkillTreeScreen");
            java.lang.reflect.Field field = screen.getDeclaredField("CATEGORY_COUNT");
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return HOST_CATEGORY_COUNT_FALLBACK;
        }
    }

    /**
     * 「神明之力」页签的索引：紧跟宿主最后一个页签之后。
     * <p>不写死 11/12，而是 {@code 宿主页签数}，所以本体加页签时我们的页签自动后移。
     */
    public static int divineCategoryIndex() { return hostCategoryCount(); }

    /** 页签总数 = 宿主页签数 + 我们追加的 1 个。 */
    public static int categoryCount() { return hostCategoryCount() + 1; }
    /** 宿主 CATEGORY_TITLE_KEYS 里追加的键；完整键为 ui.zifeng_s_custom_skill_tree.col_divine。 */
    public static final String DIVINE_CATEGORY_KEY = "col_divine";
    /** 页签强调色（ARGB，与宿主 CATEGORY_COLORS 同格式）。 */
    public static final int DIVINE_CATEGORY_COLOR = 0xFFB388FF;

    /**
     * 「万机之神的加护」的等级上限。
     * <p>第 6 级不再是倍率：它的效果是「验证 1 批 → 瞬成整单」，见 {@code DivineTranscend}。
     */
    public static final int DIVINE_MAX_LEVEL = 6;
    /**
     * 六级倍率表：索引即等级。1→×2 … 5→×1024。
     * <p>索引 6 刻意仍填 1024：第 6 级没有更高倍率，填 1024 只是让所有
     * 「按等级取倍率」的既有代码（加速、排序、clamp）拿到一个不越界且不虚高的值。
     */
    private static final int[] DIVINE_MULTIPLIERS = {1, 2, 8, 16, 128, 1024, 1024};
    /** 非等比费用，不能套用 base<<n。第 6 级由用户指定为 1,000,000 点。 */
    private static final long[] DIVINE_COSTS = {1000L, 5000L, 10000L, 50000L, 100000L, 1000000L};

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
    /** 第 6 级专属成就。 */
    public static final String ACH_TRANSCEND = "divine_transcendence";
    /**
     * 万机之神的「旧」满级成就门槛：<b>必须钉成常量 5</b>。
     * <p>它原本由 {@code maxLevel(id)} 推导。一旦 {@link #DIVINE_MAX_LEVEL} 变成 6，
     * 沿用 maxLevel 会让 5 级成就偷偷改成「6 级才发」，已有存档再也拿不到它。
     * 门槛从此固定为 5，6 级另发 {@link #ACH_TRANSCEND}。
     */
    public static final int ACH_MACHINE_LEVEL = 5;
    /** 该技能满级时对应的成就后缀；没有成就的技能返回 {@code null}。 */
    public static String achievementPath(String id) {
        return switch (id) {
            case DIVINE_MACHINE -> ACH_MACHINE;
            case ASSEMBLER_INFINITE -> ACH_ASSEMBLER;
            case CPU_INFINITE -> ACH_CPU;
            default -> null;
        };
    }
    /**
     * 按「技能 + 当前等级」求应发放的成就后缀：没到门槛返回 {@code null}。
     * <p>万机之神有两级门槛（5 / 6），其余技能仍只有一个满级成就。
     */
    public static String achievementPathFor(String id, int level) {
        if (DIVINE_MACHINE.equals(id)) {
            if (level >= DIVINE_MAX_LEVEL) return ACH_TRANSCEND;
            return level >= ACH_MACHINE_LEVEL ? ACH_MACHINE : null;
        }
        String path = achievementPath(id);
        return path != null && level >= maxLevel(id) ? path : null;
    }
    public static int maxLevel(String id) {
        if (id.equals(ASSEMBLER_SPEED) || id.equals(CPU_SPEED)) return 4;
        // 「合成之神 / 算力之神」保持 5 级；只有万机之神有第 6 级。
        return DIVINE_MACHINE.equals(id) ? DIVINE_MAX_LEVEL : 5;
    }
    public static int divineMultiplier(int level) { return DIVINE_MULTIPLIERS[clamp(level, 0, DIVINE_MULTIPLIERS.length - 1)]; }
    public static long cost(String id, int current) {
        if (isDivine(id)) return DIVINE_COSTS[clamp(current, 0, DIVINE_COSTS.length - 1)];
        long base = id.equals(ASSEMBLER_SPEED) ? 500 : id.equals(CPU_SPEED) ? 1000 : 2000;
        return base << clamp(current, 0, maxLevel(id) - 1);
    }
    /** {@code Math.clamp} 是 Java 21 API；1.20.1 跑在 Java 17 上，这里自带一份。 */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
    public static Item icon(String id) {
        return switch (id) {
            case ASSEMBLER_SPEED -> Items.CRAFTING_TABLE;
            case CPU_SPEED -> Items.COMPARATOR;
            case ASSEMBLER_INFINITE -> Items.END_CRYSTAL;
            case DIVINE_MACHINE -> Items.NETHER_STAR;
            default -> Items.AMETHYST_CLUSTER;
        };
    }
}