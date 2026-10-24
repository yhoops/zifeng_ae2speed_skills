package org.zifeng.ae2skills.advancement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.SkillDefinitions;

/**
 * 「神明之力」三项技能的满级成就：<b>每个成就整个存档只发放一次</b>，
 * 分别在有人把对应技能真正激活到满级时触发。
 *
 * <ul>
 *   <li>万机之神的加护 → {@code divine_machine_grace}</li>
 *   <li>合成之神的加护 → {@code divine_assembler_grace}</li>
 *   <li>算力之神的加护 → {@code divine_cpu_grace}</li>
 * </ul>
 *
 * <p>发放走原版 advancement 系统（存档内可见、可被其他数据包引用、随存档保存），
 * 「只第一次」由 {@link State} 这个原版 {@link SavedData} 保证，而不是内存标记，
 * 因此重启服务器后不会被重复触发。
 *
 * <h2>为什么还要一个内存短路</h2>
 * 调用点在每 tick 的技能状态刷新里，满级时会每 tick 命中一次。{@link MinecraftServer#overworld()}
 * 的 {@code DataStorage#computeIfAbsent} 与 {@code AdvancementManager#get} 都是 Map 查找，
 * 单次很便宜但没必要每 tick 做；{@link #GRANTED_THIS_SESSION} 命中后直接返回。
 * 它只是缓存，判定依据始终是存档里的 NBT 布尔值。
 */
public final class DivineAdvancements {
    private static final Logger LOG = LoggerFactory.getLogger(Addon.MOD_ID);
    /** 万机之神的成就；保留该常量名供既有测试与调用方引用。 */
    public static final ResourceLocation ID = id(SkillDefinitions.ACH_MACHINE);
    public static final String CRITERION = "first_activation";
    private static final String DATA_NAME = "zifeng_ae2_skills_divine_grace";
    /**
     * 万机之神的存档 NBT 键。1.1.6 及更早版本只写这一个键，
     * 保持不变才能让老存档「已发放」状态继续生效、不会被重复触发。
     */
    public static final String GRANTED_KEY = "Granted";
    /** 四项成就的后缀表，用于序列化时逐一落盘。 */
    private static final List<String> ALL_PATHS = List.of(SkillDefinitions.ACH_MACHINE,
            SkillDefinitions.ACH_TRANSCEND, SkillDefinitions.ACH_ASSEMBLER, SkillDefinitions.ACH_CPU);
    /** 本次服务器运行内已经确认发放过的成就，用于跳过每 tick 的存档查询。 */
    private static final Set<String> GRANTED_THIS_SESSION = ConcurrentHashMap.newKeySet();
    private DivineAdvancements() {}

    /** 由成就后缀拼出完整 advancement id。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Addon.MOD_ID, path);
    }

    /** 万机之神沿用历史键名 {@code Granted}，其余成就各用一个独立布尔键。 */
    private static String nbtKey(String path) {
        return SkillDefinitions.ACH_MACHINE.equals(path) ? GRANTED_KEY : "Granted_" + path;
    }

    /** 存档级「已经发放过」标记，按成就后缀分别记录。 */
    public static final class State extends SavedData {
        private final Set<String> granted = new HashSet<>();

        public static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            for (String path : ALL_PATHS) {
                if (tag.getBoolean(nbtKey(path))) state.granted.add(path);
            }
            return state;
        }

        /** 任一成就已发放；保留旧签名供既有测试调用。 */
        public boolean granted() { return granted.contains(SkillDefinitions.ACH_MACHINE); }

        public boolean granted(String path) { return granted.contains(path); }

        /** 标记万机之神已发放；保留旧签名供既有测试调用。 */
        public void markGranted() { markGranted(SkillDefinitions.ACH_MACHINE); }

        public void markGranted(String path) {
            granted.add(path);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            for (String path : ALL_PATHS) {
                tag.putBoolean(nbtKey(path), granted.contains(path));
            }
            return tag;
        }
    }

    /** 存档重置（换存档/回滚）后需要重新允许发放；由服务器停止事件调用。 */
    public static void resetCache() { GRANTED_THIS_SESSION.clear(); }

    /**
     * 按「技能 + 当前等级」发放对应的满级成就。
     * <p>调用点在每 tick 的状态刷新里，因此这里必须先做等级与缓存的短路；
     * 真正的「只发一次」仍由存档 NBT 保证。
     *
     * @return 本次调用是否<b>真的</b>发放了成就（用于雷声等一次性演出；
     *         已发放过、等级不够、数据包缺失都返回 {@code false}）
     */
    public static boolean grant(ServerPlayer player, String skillId, int level) {
        return grantPath(player, SkillDefinitions.achievementPathFor(skillId, level));
    }

    /** 直接按成就后缀发放；{@code null} 表示该等级还没到任何门槛。 */
    public static boolean grantPath(ServerPlayer player, String path) {
        if (path == null || GRANTED_THIS_SESSION.contains(path)) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        State state = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(State::new, State::load), DATA_NAME);
        if (state.granted(path)) { GRANTED_THIS_SESSION.add(path); return false; }
        ResourceLocation advancementId = id(path);
        AdvancementHolder advancement = server.getAdvancements().get(advancementId);
        if (advancement == null) {
            // 数据包缺失/被其它数据包覆盖：不静默，留一条可诊断的日志。
            LOG.warn("成就 {} 未在数据包中加载，无法发放；请检查本模组的数据包是否被覆盖 / "
                    + "Advancement {} is missing from the loaded datapack", advancementId, advancementId);
            return false;
        }
        state.markGranted(path);
        GRANTED_THIS_SESSION.add(path);
        player.getAdvancements().award(advancement, CRITERION);
        return true;
    }

    /** 按技能 id 发放「当前等级对应的」成就；保留旧签名供既有调用与测试。 */
    public static void grant(ServerPlayer player, String skillId) {
        grantPath(player, SkillDefinitions.achievementPath(skillId));
    }

    /** 万机之神的旧入口；保留以兼容既有调用。 */
    public static void grantFirstActivation(ServerPlayer player) {
        grant(player, SkillDefinitions.DIVINE_MACHINE);
    }
}
