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
        // 主配置仍保持 ERROR：主配置失败意味着四个核心技能无法安全接入，继续跑会留下半应用状态。
        if (config.equals("zifeng_ae2_skills.compat.mixins.json")) {
            LoggerFactory.getLogger("zifeng_ae2_skills").warn(
                    "AE2 技能第三方适配混入失败，跳过该适配（主功能不受影响）/ "
                            + "Optional AE2 skill adapter mixin failed for {}; adapter skipped, core features unaffected.",
                    target, error);
            return ErrorAction.WARN;
        }
        if (!config.equals("zifeng_ae2_skills.mixins.json")) return action;
        LoggerFactory.getLogger("zifeng_ae2_skills").error(
                "AE2 技能附属无法安全接入目标 {}，停止加载。请检查 AE2/原作者技能树版本或其他修改合成的模组。 / "
                        + "Zifeng AE2 Skills transformation failed for {}. Update this addon or use compatible AE2/original host mods; "
                        + "check other crafting mixins. See logs/latest.log for the failing injection.", target, target, error);
        return ErrorAction.ERROR;
    }
}
