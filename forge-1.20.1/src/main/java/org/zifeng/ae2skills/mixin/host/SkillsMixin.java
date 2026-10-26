package org.zifeng.ae2skills.mixin.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.skilltree.skill.Skills;

@Mixin(value = Skills.class, remap = false)
public abstract class SkillsMixin {
    @Shadow @Final @Mutable public static List<String> GLOBAL_SKILLS;
    @Shadow @Final @Mutable public static List<String> ALL_SKILLS;
    @Shadow @Final @Mutable private static Map<String, Skills.SkillType> TYPE_MAP;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void addon$register(CallbackInfo ci) {
        var global = new ArrayList<>(GLOBAL_SKILLS);
        var all = new ArrayList<>(ALL_SKILLS);
        var types = new HashMap<>(TYPE_MAP);
        for (String id : SkillDefinitions.IDS) {
            if (all.contains(id)) throw new IllegalStateException("AE2 技能重复 / Duplicate AE2 skill: " + id
                    + ". 请更换为未内置四技能的原作者本体 / Install the original skill-tree host.");
            global.add(id); all.add(id); types.put(id, Skills.SkillType.GLOBAL);
        }
        GLOBAL_SKILLS = List.copyOf(global);
        ALL_SKILLS = all;
        TYPE_MAP = types;
    }
    @Inject(method = "getGlobalMaxPoints", at = @At("HEAD"), cancellable = true)
    private static void addon$max(String id, CallbackInfoReturnable<Integer> ci) {
        if (SkillDefinitions.contains(id)) ci.setReturnValue(SkillDefinitions.maxLevel(id));
    }
    @Inject(method = "getGlobalCost", at = @At("HEAD"), cancellable = true)
    private static void addon$cost(String id, int current, CallbackInfoReturnable<Long> ci) {
        if (SkillDefinitions.contains(id)) ci.setReturnValue(SkillDefinitions.cost(id, current));
    }
    @Inject(method = "getIcon", at = @At("HEAD"), cancellable = true)
    private static void addon$icon(String id, CallbackInfoReturnable<Item> ci) {
        if (SkillDefinitions.contains(id)) ci.setReturnValue(SkillDefinitions.icon(id));
    }
}
