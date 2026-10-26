package org.zifeng.ae2skills.mixin.host;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.skill.Skills;

/**
 * 宿主把页签数量写死成 {@code CATEGORY_COUNT}，本 Mixin 在它之后追加第 N+1 个「神明之力」页签。
 *
 * <h2>为什么不再用 {@code @ModifyConstant}</h2>
 * 旧做法是把字面量 {@code 11}（1.21.1）或 {@code 12}（1.20.1）换成 {@code 页签数+1}。
 * 这有两个问题：
 * <ol>
 *   <li>匹配值本身写死。本体一旦新增页签，{@code @Constant(intValue=12)} 就匹配不上，
 *       {@code require} 会让整个 Mixin 应用失败、游戏起不来。</li>
 *   <li>{@code CATEGORY_COUNT} 在宿主字节码里被完全内联成 {@code bipush}，
 *       全局 0 处 {@code getstatic}，所以没有「改一个常量、处处生效」的捷径。</li>
 * </ol>
 * 现在的做法是：<b>完全不碰宿主的字面量</b>，改为在宿主自己的方法返回后「补一条」。
 * 宿主页签数在运行时从 {@code CATEGORY_COUNT} 反射读出（见 {@link SkillDefinitions#hostCategoryCount()}），
 * 本体将来加页签，我们自动后移，无需改代码。
 *
 * <h2>三处补齐</h2>
 * <ol>
 *   <li>{@code <clinit>} TAIL：两张静态表（标题键 / 颜色）按宿主长度复制后追加我们那一项。</li>
 *   <li>{@code <init>} TAIL：三个实例数组（{@code catButtonX/catButtonW/catTitles}）按宿主长度重新分配。
 *       宿主构造器里按字面量分配，长度恰好够宿主页签；不扩会在 {@code rebuildCategoryRow}
 *       写入我们页签的索引时数组越界。</li>
 *   <li>{@code refreshDisplayedCategories} TAIL：把我们的页签索引追加进 {@code displayedCategories}，
 *       并通过宿主自己的 {@code isSkillVisible} 判断是否显示。</li>
 *   <li>{@code categoryIndexOf} RETURN：宿主找不到时返回 {@code -1}，我们把索引补上。</li>
 * </ol>
 *
 * <h2>降级策略</h2>
 * 所有注入点都 {@code require = 1}：宿主结构一变就整体失败并明确报错，
 * 不会出现「能进游戏但页签错位」的静默错误。反射失败时退到 {@code HOST_CATEGORY_COUNT_FALLBACK}。
 */
@Mixin(value = SkillTreeScreen.class, remap = false)
public abstract class SkillTreeCategoryMixin {
    @Shadow @Final @Mutable private static String[] CATEGORY_TITLE_KEYS;
    @Shadow @Final @Mutable private static int[] CATEGORY_COLORS;
    // 宿主这三个数组是 final，要重新赋值必须同时标 @Final @Mutable
    @Shadow @Final @Mutable private int[] catButtonX;
    @Shadow @Final @Mutable private int[] catButtonW;
    @Shadow @Final @Mutable private String[] catTitles;
    @Shadow @Final private List<Integer> displayedCategories;
    @Shadow protected abstract boolean isSkillVisible(String id);

    /** 两张静态表按宿主页签数创建，这里补成「宿主数 + 1」。 */
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void addon$extendCategoryTables(CallbackInfo ci) {
        int host = CATEGORY_TITLE_KEYS.length;
        int total = host + 1;
        String[] keys = Arrays.copyOf(CATEGORY_TITLE_KEYS, total);
        keys[host] = SkillDefinitions.DIVINE_CATEGORY_KEY;
        int[] colors = Arrays.copyOf(CATEGORY_COLORS, total);
        colors[host] = SkillDefinitions.DIVINE_CATEGORY_COLOR;
        CATEGORY_TITLE_KEYS = keys;
        CATEGORY_COLORS = colors;
    }

    /**
     * 宿主构造器按字面量分配三个实例数组，长度只够宿主自己的页签。
     * {@code rebuildCategoryRow} 会按 {@code displayedCategories} 里的索引写这些数组，
     * 所以必须在构造结束前把它们扩到「宿主数 + 1」。
     */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void addon$growCategoryArrays(CallbackInfo ci) {
        int total = SkillDefinitions.categoryCount();
        if (catButtonX.length < total) catButtonX = Arrays.copyOf(catButtonX, total);
        if (catButtonW.length < total) catButtonW = Arrays.copyOf(catButtonW, total);
        if (catTitles.length < total) catTitles = Arrays.copyOf(catTitles, total);
    }

    /**
     * 宿主自己按 {@code for (i < CATEGORY_COUNT)} 收集可见页签，收集完我们把我们的补上。
     * 用宿主的 {@code isSkillVisible} 判断，语义与它自己筛页签时一致。
     */
    @Inject(method = "refreshDisplayedCategories", at = @At("TAIL"), require = 1)
    private void addon$appendDivineCategory(CallbackInfo ci) {
        int index = SkillDefinitions.divineCategoryIndex();
        if (displayedCategories.contains(index)) return;
        for (String id : SkillDefinitions.divineSkills()) {
            if (isSkillVisible(id)) {
                displayedCategories.add(index);
                break;
            }
        }
        // 宿主原本会把「当前选中页签不在可见列表里」时回退到第一个可见页签。
        // 我们的页签被追加后可能仍不可见，这里不改宿主的选择逻辑，保持它的行为。
    }

    /**
     * 宿主按 {@code for (i < CATEGORY_COUNT)} 找页签索引，找不到返回 {@code -1}。
     * 我们的页签索引是宿主之后那个，宿主永远找不到，所以在 RETURN 时补上。
     */
    @Inject(method = "categoryIndexOf", at = @At("RETURN"), cancellable = true, require = 1)
    private static void addon$divineCategoryIndex(String skillId, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() >= 0) return;
        if (SkillDefinitions.inDivineTab(skillId)) {
            cir.setReturnValue(SkillDefinitions.divineCategoryIndex());
        }
    }

    /** 「寰宇法则」（索引 6）去掉神明页签成员后的稳定列表：宿主每次搜索/点击都会枚举所有页签，缓存避免反复过滤。 */
    @Unique
    private static List<String> addon$globalWithoutDivine;

    /**
     * 第 N+1 个页签的成员表；同时在「寰宇法则」（索引 6）里隐藏这些技能，
     * 避免同一个技能同时出现在两个页签。
     */
    @Inject(method = "categorySkills", at = @At("HEAD"), cancellable = true, require = 1)
    private static void addon$divineCategory(int index, CallbackInfoReturnable<List<String>> ci) {
        if (index == SkillDefinitions.divineCategoryIndex()) {
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