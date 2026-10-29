package org.zifeng.ae2skills.compat;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 进游戏后在聊天框里告诉玩家「哪里出问题了、日志在哪」。
 *
 * <h2>为什么要做这一步</h2>
 * 1.2.1 起混入失败不再让游戏崩溃（见 {@link MixinFailureHandler}），代价是玩家很可能
 * 压根不知道自己装的附属没生效——表现只是「技能点了没用」。这条提示把三件事一次说清：
 * <ol>
 *   <li>附属确实加载了，但功能被停用；</li>
 *   <li>具体是哪个目标没接上（多半是别的模组以更高优先级覆盖了同一个方法）；</li>
 *   <li>日志文件的完整路径，方便直接贴给作者。</li>
 * </ol>
 *
 * <h2>为什么用 {@code translatableWithFallback}</h2>
 * 提示由服务端发进聊天框，但译文在客户端。装了本附属的客户端会读到对应语言的键；
 * 万一客户端没装（或语言文件缺键），退回固定的中文原文，而不是把
 * {@code ui.zifeng_ae2_skills.*} 这种原始键名甩在玩家脸上。
 */
public final class DegradedNotice {
    private DegradedNotice() {}

    /** 最多往聊天框里列的条数：再多也只是刷屏，剩下的都在日志里。 */
    private static final int MAX_LINES = 6;

    /** 玩家本次登录是否已经提示过，避免重进服务器时刷屏。 */
    private static final java.util.Set<java.util.UUID> NOTIFIED = new java.util.HashSet<>();

    /** 服务器关闭或玩家登出时清理，换存档后仍会重新提示。 */
    public static synchronized void reset() { NOTIFIED.clear(); }

    public static synchronized void announce(ServerPlayer player) {
        if (player == null) return;
        if (!Compatibility.degraded() && Compatibility.enabled()) return;
        if (!NOTIFIED.add(player.getUUID())) return;

        player.sendSystemMessage(header());
        for (String line : reasons()) {
            player.sendSystemMessage(Component.literal("· " + line).withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component
                .translatableWithFallback("ui.zifeng_ae2_skills.degraded_log",
                        "完整报错在：%s（反馈时请直接附上这个文件）", logPath().toString())
                .withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 需要告诉玩家的原因，最多 {@link #MAX_LINES} 行。
     *
     * <p>两类原因分开取：「注入被别的模组抢走」（{@link Compatibility#degradedReasons()}，
     * 逐条是短句）与「预检就没过」（{@link Compatibility#problems()}，本身已经是条目列表）。
     * 后者不能拿 {@link Compatibility#diagnostic()} 顶上——那是一整段含版本号与英文说明的
     * 长文本，逐行塞进聊天框会刷出七八条，而且说的不是同一件事。
     */
    private static List<String> reasons() {
        String degraded = Compatibility.degradedReasons();
        String joined = degraded;
        if (joined == null || joined.isBlank()) {
            List<String> problems = Compatibility.problems();
            joined = problems.isEmpty() ? "" : String.join("\n", problems);
        }
        var lines = new java.util.ArrayList<String>();
        // degradedReasons() 用全角分号把多条原因连成一行，problems() 是逐行列表，
        // 两种分隔都切开，聊天框里才是一行一条。
        for (String line : joined.split("\n|；")) {
            if (line.isBlank()) continue;
            if (lines.size() >= MAX_LINES) { lines.add("…"); break; }
            lines.add(line.trim());
        }
        return lines;
    }

    private static Component header() {
        return Component
                .translatableWithFallback("ui.zifeng_ae2_skills.degraded_header",
                        "【子枫 AE2 技能】本附属功能已停用：技能不会生效，但游戏可以正常游玩。原因：")
                .withStyle(ChatFormatting.RED);
    }

    /** 日志绝对路径；取不到时退回 Minecraft 惯例的相对路径。 */
    private static Path logPath() {
        try {
            return FMLPaths.GAMEDIR.get().resolve("logs").resolve("latest.log");
        } catch (LinkageError | RuntimeException error) {
            return Path.of("logs", "latest.log");
        }
    }
}