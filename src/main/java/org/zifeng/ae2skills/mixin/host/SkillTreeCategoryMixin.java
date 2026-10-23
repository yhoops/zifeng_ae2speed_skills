package org.zifeng.ae2skills.mixin.host;

import java.util.Arrays;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.skill.Skills;

/**
 * 宿主把页签数量写死成 11（构造期 {@code new int[11]}、两张静态表、若干 {@code i < 11} 循环）。
 * 本 Mixin 把它扩到 12，第 12 个页签是「神明之力」。
 *
 * <h2>为什么用 {@code @ModifyConstant} 而不是重写方法</h2>
 * 宿主这些方法体都很长（{@code mouseClickedInner} 数百行），整段 {@code @Redirect}/{@code @Overwrite}
 * 会把宿主逻辑复制一份，宿主升级时静默走偏。这里只把字面量 11 换成 12，其余字节码原样保留。
 *
 * <h2>降级策略</h2>
 * 每个注入点都带精确的 {@code require}：宿主结构一旦不再匹配，Mixin 应用失败 →
 * 由 {@code MixinFailureHandler} 报错并整体停止加载，不会出现「能进游戏但页签错位」的中间状态。
 *
 * <h2>宿主 1.5.0 / 1.5.5 双版本</h2>
 * 1.5.5 把「页签数量写死 11」重构为动态列表 {@code displayedCategories}：
 * {@code rebuildCategoryRow / renderCategoryRow / mouseClickedInner} 改为按
 * {@code displayedCategories.size()} 遍历，不再有字面量 11（这些注入对 1.5.5 失效，故
 * {@code require=0} 静默跳过，对 1.5.0 仍校验命中）。决定「哪些页签可见」的 11 挪到了
 * 1.5.5 新增的 {@code refreshDisplayedCategories}（{@code for (i < 11)}），此方法 1.5.0 不存在，
 * 故对 1.5.0 设 {@code require=0}。两版本共享的 {@code <init>} 数组、{@code <clinit>} 扩表、
 * {@code categorySkills}、{@code categoryIndexOf}、{@code searchResults} 均保持 {@code require>=1}。
 */
@Mixin(value = SkillTreeScreen.class, remap = false)
public abstract class SkillTreeCategoryMixin {
    @Shadow @Final @Mutable private static String[] CATEGORY_TITLE_KEYS;
    @Shadow @Final @Mutable private static int[] CATEGORY_COLORS;

    /** 两张静态表在 <clinit> 里按 11 项创建，这里补成 12 项。 */
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void addon$extendCategoryTables(CallbackInfo ci) {
        String[] keys = Arrays.copyOf(CATEGORY_TITLE_KEYS, SkillDefinitions.CATEGORY_COUNT);
        keys[SkillDefinitions.DIVINE_CATEGORY_INDEX] = SkillDefinitions.DIVINE_CATEGORY_KEY;
        int[] colors = Arrays.copyOf(CATEGORY_COLORS, SkillDefinitions.CATEGORY_COUNT);
        colors[SkillDefinitions.DIVINE_CATEGORY_INDEX] = SkillDefinitions.DIVINE_CATEGORY_COLOR;
        CATEGORY_TITLE_KEYS = keys;
        CATEGORY_COLORS = colors;
    }

    /** 构造器内 3 处 {@code new int[11] / new int[11] / new String[11]}：实例数组必须一开始就是 12 项。两版本一致。 */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 3)
    private int addon$categoryArraySize(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    /**
     * 仅 1.5.5：页签行布局的 11 挪到了 {@code refreshDisplayedCategories} 的 {@code for (i < 11)}。
     * 把这里 11→12 后，第 12 页签才会进入 {@code displayedCategories} 参与显示/布局/点击。
     * 1.5.0 无此方法，require=0 静默跳过。
     */
    @ModifyConstant(method = "refreshDisplayedCategories", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 0)
    private int addon$categoryRefreshBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    /**
     * 仅 1.5.0：这些方法体里 11 是 {@code for (i < 11)} 页签循环界，必须扩到 12。
     * 1.5.5 已改为遍历 {@code displayedCategories}（无字面量 11），此处 require=0 静默跳过。
     */
    @ModifyConstant(method = "rebuildCategoryRow", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 0)
    private int addon$categoryLayoutBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    @ModifyConstant(method = "renderCategoryRow", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 0)
    private int addon$categoryRenderBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    @ModifyConstant(method = "mouseClickedInner", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 0)
    private int addon$categoryClickBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    // 注意：categoryIndexOf / searchResults 是 static 方法，Mixin 要求回调也是 static，否则应用期直接报错。
    // 两版本都保留字面量 11，require=1 校验命中。
    @ModifyConstant(method = "categoryIndexOf", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 1)
    private static int addon$categoryLookupBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    @ModifyConstant(method = "searchResults", constant = @Constant(intValue = SkillDefinitions.HOST_CATEGORY_COUNT), require = 1)
    private static int addon$categorySearchBound(int original) { return SkillDefinitions.CATEGORY_COUNT; }

    /**
     * 第 12 个页签的成员表；同时在「寰宇法则」（索引 6）里隐藏这些技能，
     * 避免同一个技能同时出现在两个页签。
     */
    /** 「寰宇法则」去掉神明页签成员后的稳定列表：宿主每次搜索/点击都会枚举 12 个页签，缓存避免反复过滤。 */
    private static List<String> addon$globalWithoutDivine;

    @Inject(method = "categorySkills", at = @At("HEAD"), cancellable = true, require = 1)
    private static void addon$divineCategory(int index, CallbackInfoReturnable<List<String>> ci) {
        if (index == SkillDefinitions.DIVINE_CATEGORY_INDEX) {
            ci.setReturnValue(SkillDefinitions.divineSkills());
        } else if (index == 6) {
            List<String> cached = addon$globalWithoutDivine;
            // SkillsMixin 在 Skills.<clinit> 的 TAIL 才把本附属技能加进 GLOBAL_SKILLS；
            // 缓存长度与之对齐后即固定，之后不再重算。
            if (cached == null || cached.size() + SkillDefinitions.DIVINE_TAB_COUNT != Skills.GLOBAL_SKILLS.size()) {
                cached = Skills.GLOBAL_SKILLS.stream().filter(id -> !SkillDefinitions.inDivineTab(id)).toList();
                addon$globalWithoutDivine = cached;
            }
            ci.setReturnValue(cached);
        }
    }
}
