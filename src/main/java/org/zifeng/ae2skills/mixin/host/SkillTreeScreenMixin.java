package org.zifeng.ae2skills.mixin.host;

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
    @Inject(method = "tooltipFingerprint", at = @At("RETURN"), cancellable = true)
    private void addon$invalidate(SkillButton button, CallbackInfoReturnable<Integer> ci) {
        if (SkillDefinitions.contains(button.skillId())) ci.setReturnValue(31 * ci.getReturnValue() + ClientEffects.revision());
    }
}
