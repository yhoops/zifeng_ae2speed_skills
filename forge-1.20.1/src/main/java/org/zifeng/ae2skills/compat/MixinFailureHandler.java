package org.zifeng.ae2skills.compat;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.*;

/** Early transformation errors may precede FML's GUI; give an actionable log without suppressing them. */
public final class MixinFailureHandler implements IMixinErrorHandler {
    public ErrorAction onPrepareError(IMixinConfig config, Throwable error, IMixinInfo info, ErrorAction action) {
        return handle(config.getName(), "prepare", error, action);
    }
    public ErrorAction onApplyError(String target, Throwable error, IMixinInfo info, ErrorAction action) {
        return handle(info.getConfig().getName(), target, error, action);
    }
    private ErrorAction handle(String config, String target, Throwable error, ErrorAction action) {
        // 1.20.1：compat 配置已改为 required:false，这里也必须放行默认动作（WARN），
        // 不能对第三方可选适配返回 ErrorAction.ERROR —— 那会让「某个可选模组版本不匹配」升级成游戏起不来。
        if (config.equals("zifeng_ae2_skills.compat.mixins.json")) {
            LoggerFactory.getLogger("zifeng_ae2_skills").warn(
                    "AE2 技能第三方适配混入失败，跳过该适配（主功能不受影响）/ "
                            + "Optional AE2 skill adapter mixin failed for {}; adapter skipped, core features unaffected.",
                    target, error);
            return ErrorAction.WARN;
        }
        if (!config.equals("zifeng_ae2_skills.mixins.json")) return action;
        // 1.2.1（issue #1）起主配置也降级为 WARN：注入被别的模组抢走时让游戏起来，
        // 只停用受影响的功能，并在进游戏后在聊天框里告诉玩家发生了什么。
        // 这依赖 Compatibility.checkApplied() 在 FMLCommonSetupEvent 阶段做能力检测——
        // 没有它，半应用状态会在第一次跨 mixin 强制转换时抛 ClassCastException，
        // 比原来的启动崩溃更难排查。
        Compatibility.markDegraded(describe(target, error));
        LoggerFactory.getLogger("zifeng_ae2_skills").warn(
                "AE2 技能混入未能接入 {}，已停用受影响的功能（游戏继续运行）。多为其他修改 AE2 合成的模组以更高优先级覆盖了同一方法。 / "
                        + "Zifeng AE2 Skills failed to inject into {}; affected features disabled, game keeps running. "
                        + "Usually caused by another AE2/crafting mod overwriting the same method with a higher priority. "
                        + "See logs/latest.log for the failing injection.", target, target, error);
        return ErrorAction.WARN;
    }

    /** 把异常压成一句人话，用于日志与游戏内提示。 */
    private static String describe(String target, Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) message = error == null ? "unknown" : error.getClass().getSimpleName();
        // Mixin 的消息体很长（含注入器、At、调用栈上下文），只取第一行并截断。
        int newline = message.indexOf('\n');
        if (newline > 0) message = message.substring(0, newline);
        if (message.length() > 240) message = message.substring(0, 240) + "…";
        return (target == null ? "unknown" : target) + ": " + message;
    }
}
