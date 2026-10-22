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
        if (!config.equals("zifeng_ae2_skills.mixins.json")
                && !config.equals("zifeng_ae2_skills.compat.mixins.json")) return action;
        LoggerFactory.getLogger("zifeng_ae2_skills").error(
                "AE2 技能附属无法安全接入目标 {}，停止加载。请检查 AE2/原作者技能树版本或其他修改合成的模组。 / "
                        + "Zifeng AE2 Skills transformation failed for {}. Update this addon or use compatible AE2/original host mods; "
                        + "check other crafting mixins. See logs/latest.log for the failing injection.", target, target, error);
        return ErrorAction.ERROR;
    }
}
