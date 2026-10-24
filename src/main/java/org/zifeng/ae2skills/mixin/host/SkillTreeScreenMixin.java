package org.zifeng.ae2skills.mixin.host;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.ae2skills.client.ClientEffects;
import org.zifeng.skilltree.client.screen.SkillButton;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.skill.Skills;

@Mixin(value = SkillTreeScreen.class, remap = false)
public abstract class SkillTreeScreenMixin {
    @Shadow private Map<String, Integer> learnedSkills;
    @Shadow private Map<String, Integer> activeLevels;
    @Shadow private Map<String, Boolean> toggles;
    /** tooltip 缓存里当前正在构建/展示的技能 id；本类的视觉注入靠它判定「是不是本模组的技能」。 */
    @Shadow private String tooltipCacheSkillId;
    /** 当前悬浮 tooltip 的矩形 [x, y, w, h]；未悬浮时为 null。 */
    @Shadow private int[] activeTooltipBounds;

    @Inject(method = "canLearn", at = @At("HEAD"), cancellable = true)
    private void addon$cap(String id, CallbackInfoReturnable<Boolean> ci) {
        if (SkillDefinitions.contains(id) && learnedSkills.getOrDefault(id, 0) >= SkillDefinitions.maxLevel(id)) ci.setReturnValue(false);
    }
    @Inject(method = "skillAttrText", at = @At("HEAD"), cancellable = true)
    private void addon$effect(String id, int points, boolean noMod, CallbackInfoReturnable<String> ci) {
        if (SkillDefinitions.contains(id)) ci.setReturnValue(ClientEffects.value(id,
                toggles.getOrDefault(id, true) ? activeLevels.getOrDefault(id, points) : 0));
    }
    @Redirect(method = "buildTooltipLines", at = @At(value = "INVOKE",
            target = "Lorg/zifeng/skilltree/skill/Skills;getDescriptionComponent(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"))
    private Component addon$description(String id) {
        var original = Skills.getDescriptionComponent(id);
        if (!SkillDefinitions.contains(id)) return original;
        return original.copy().append("\n").append(Component.translatable("ui.zifeng_ae2_skills.server_effect",
                ClientEffects.value(id, ClientEffects.level(id))));
    }
    /**
     * 费用千分位。宿主 {@code fmtCost} 的原文是「整数就 {@code String.valueOf((long)d)}」，
     * 于是 1,000,000 会被画成裸露的 {@code 1000000}，在按钮上挤成一串。
     *
     * <p>阈值刻意取 100000：宿主现有技能费用最高 32000，加上千分位对它们毫无变化（零回归）；
     * 宿主自身最大的费用常量是 720000（{@code getGiftCost} 的 gift_*_amp 档），
     * 会一并变成 {@code 720,000}——纯显示美化，不改任何数值语义。
     *
     * <p>非整数值直接放行，交回宿主原来的 {@code %.1f} 分支，避免把小数费用截断。
     * 该方法全部 12 处调用点的实参都来自 {@code Skills.getGlobalCost / getGiftCost /
     * getFishingCost / nextCostLocal}，不会误伤非费用文本。
     */
    @Inject(method = "fmtCost", at = @At("RETURN"), cancellable = true, require = 1)
    private static void addon$groupDigits(double value, CallbackInfoReturnable<String> ci) {
        if (value < 100_000D || value != Math.floor(value)) return;
        ci.setReturnValue(String.format(java.util.Locale.ROOT, "%,d", (long) value));
    }
    @Inject(method = "tooltipFingerprint", at = @At("RETURN"), cancellable = true)
    private void addon$invalidate(SkillButton button, CallbackInfoReturnable<Integer> ci) {
        if (SkillDefinitions.contains(button.skillId())) ci.setReturnValue(31 * ci.getReturnValue() + ClientEffects.revision());
    }

    // ───────────────────────── 视觉：类型前缀 / 标题配色 / 悬浮图标 ─────────────────────────

    /** 宿主把 GLOBAL 类型的 tooltip 前缀写成「寰宇」，本模组的技能改显示「诸神」。 */
    private static final String TYPE_GLOBAL_KEY = "ui.zifeng_s_custom_skill_tree.type_global";
    private static final String TYPE_DIVINE_KEY = "ui.zifeng_ae2_skills.type_divine";
    /** 与「神明之力」页签同色的紫。 */
    private static final int DIVINE_TITLE_COLOR = SkillDefinitions.DIVINE_CATEGORY_COLOR;

    /**
     * tooltip 标题的前缀取自 {@code Component.translatable(<type key>)}。
     * 该方法体内 10 个类型各调一次，这里按 key 值精确挑出 GLOBAL 那一次，
     * 且只在当前 tooltip 属于「神明之力」页签的技能时替换成「诸神」——
     * 仍留在「寰宇法则」页签的超频技能保持原来的「寰宇」，与页签归属一致。
     */
    @ModifyArg(method = "buildTooltipLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 0, require = 0)
    private String addon$divineTypePrefix(String key) {
        if (TYPE_GLOBAL_KEY.equals(key) && SkillDefinitions.inDivineTab(tooltipCacheSkillId)) return TYPE_DIVINE_KEY;
        return key;
    }

    /**
     * 标题行颜色是 {@code buildTooltipLines} 里按 SkillType 分支取的常量，GLOBAL 对应 -10040065
     * （与 GLOBAL 的「寰宇」前缀同一个 case，已按枚举顺序 MAGIC/BASE/AMPLIFY/ULTIMATE/SPECIAL/
     * AURA/GLOBAL/MACHINE/GIFT/FISHING 与两处 tableswitch 对照确认）。神明页签的技能改成紫色。
     * 同一方法内这个常量还被「天气切换」提示行使用，但那条提示只对带天气开关的技能生效，
     * 本模组的 AE 技能不会走到，因此对我们没有副作用。
     */
    @ModifyConstant(method = "buildTooltipLines", constant = @Constant(intValue = -10040065), require = 0)
    private int addon$divineTitleColor(int color) {
        return SkillDefinitions.inDivineTab(tooltipCacheSkillId) ? DIVINE_TITLE_COLOR : color;
    }

    /**
     * 悬浮时图标消失的根因：宿主 {@code isIconUnderUI} 把 {@code activeTooltipBounds} 也当作
     * 「覆盖图标的 UI」，命中就把图标整个跳过（{@code renderSkillButton} 里 {@code ifne} 直接跳到画名字处，
     * 不会补画图标）。本模组技能的描述很长，{@code activeTooltipBounds} 用的是未展开的行宽，
     * 宽度超出鼠标右侧空间时会被 {@code computeTooltipLayout} 翻到左边并夹到屏幕左缘（x=10），
     * 正好压住行首图标（图标在 {@code rowLeft()+3} 起、16px 宽）。
     *
     * <p>修法：只要 tooltip 落到左侧图标列，就改成贴右对齐，而不是压在图标上。
     * 正常靠右显示的 tooltip 完全不动。
     */
    @Inject(method = "computeTooltipLayout", at = @At("RETURN"), require = 0)
    private void addon$keepTooltipOffIcon(List<?> lines, int mouseX, int mouseY, CallbackInfoReturnable<int[]> ci) {
        if (!SkillDefinitions.contains(tooltipCacheSkillId)) return;
        int[] bounds = ci.getReturnValue();
        if (bounds == null || bounds.length < 4) return;
        // 32 = 行首图标列右缘（rowLeft()+3+16，再留一点间隙）的安全下界。
        if (bounds[0] >= 32) return;
        int limit = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - 10;
        bounds[0] = Math.max(32, limit - bounds[2]);
    }

    /**
     * 兜底：万一 tooltip 仍然压到图标（极端窄窗口），也不要因此把图标藏起来——
     * 只忽略「tooltip 造成的遮挡」，右面板/子界面等其它遮挡逻辑保持宿主原样。
     */
    @Inject(method = "isIconUnderUI", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$ignoreTooltipOverlap(SkillButton button, CallbackInfoReturnable<Boolean> ci) {
        if (activeTooltipBounds != null && SkillDefinitions.contains(button.skillId())) ci.setReturnValue(false);
    }
}
