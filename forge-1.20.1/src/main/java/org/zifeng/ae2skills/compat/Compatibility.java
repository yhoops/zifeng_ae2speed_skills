package org.zifeng.ae2skills.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import com.google.gson.JsonParser;
// 1.20.1：net.neoforged.fml.ModLoadingException / ModLoadingIssue 都不存在；
// 这两处能力已由 report() 的「只记日志、不抛异常」替代，因此不再需要对应 import。
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.zifeng.ae2skills.crafting.CpuExtension;
import org.zifeng.ae2skills.crafting.InstantCpuExtension;
import org.zifeng.ae2skills.crafting.JobAccess;
import org.zifeng.ae2skills.crafting.TimeAccess;
import org.zifeng.ae2skills.mixin.ae2.CpuLogicAccess;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.service.MixinService;

/** Structural compatibility, not a claim that arbitrary future releases behave identically. */
public final class Compatibility {
    private static final String HOST = "org/zifeng/skilltree/";
    private static final String OURS = "org/zifeng/ae2skills/";
    private static final Map<String, ClassNode> CLASSES = new HashMap<>();
    private static final Set<String> PROBLEMS = new LinkedHashSet<>();
    private static boolean checked;
    private static String versions = "";
    /** 0 = 还没检查，1 = 可用，2 = 不可用。让 {@link #enabled()} 在热路径上无锁。 */
    private static volatile int state;
    private Compatibility() {}
    /**
     * 本附属是否可用。
     * <p>技能等级在每个玩家每 tick 都会被重新计算，这条路径不能每次都进同步块，
     * 因此检查结果缓存在 {@link #state} 里，只有第一次调用会真正跑预检。
     */
    public static boolean enabled() {
        int current = state;
        if (current == 0) { check(); current = state; }
        return current == 1;
    }
    public static synchronized void check() {
        if (checked) return;
        checked = true;
        try {
            for (String id : List.of("zifeng_s_custom_skill_tree", "ae2")) {
                var mod = LoadingModList.get().getMods().stream().filter(m -> m.getModId().equals(id)).findFirst();
                if (mod.isEmpty()) PROBLEMS.add("缺少依赖 / Missing dependency: " + id);
                else versions += id + "=" + mod.get().getVersion() + "; ";
            }
            if (!PROBLEMS.isEmpty()) return;
            var host = read(HOST + "skill/Skills");
            Set<String> ids = Set.of("ae_assembler_speed", "ae_cpu_speed", "ae_assembler_infinite", "ae_cpu_infinite");
            boolean duplicate = host.fields.stream().anyMatch(f -> f.value instanceof String s && ids.contains(s));
            for (var method : host.methods) for (var insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && ids.contains(s)) duplicate = true;
            if (duplicate) {
                PROBLEMS.add("本体已内置四技能 / Duplicate built-in AE2 skills. 请换为原作者本体，不能与定制 1.5.3 同装 / Install the original host, not custom 1.5.3.");
                return;
            }
            var config = JsonParser.parseString(new String(resource("zifeng_ae2_skills.mixins.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = config.get("package").getAsString().replace('.', '/') + "/";
            boolean client = FMLLoader.getDist().isClient();
            Set<String> clientMixins = new HashSet<>();
            for (var name : config.getAsJsonArray("client")) clientMixins.add(pkg + name.getAsString().replace('.', '/'));
            for (var name : config.getAsJsonArray("mixins")) checkMixin(read(pkg + name.getAsString().replace('.', '/')));
            if (client) for (String name : clientMixins) checkMixin(read(name));
            String index = new String(resource("zifeng-ae2-contract-classes.txt"), StandardCharsets.UTF_8);
            for (String name : index.split("\\R")) {
                if (name.isBlank() || name.startsWith(OURS + "compat/")
                        || name.startsWith(OURS + "mixin/compat/") || name.contains("/test/")) continue;
                if (aePowerClass(name)) continue; // AE power has an independent compatibility check.
                if (!client && (name.startsWith(OURS + "client/") || clientMixins.contains(name))) continue;
                checkCalls(read(name));
            }
        } catch (Exception | LinkageError e) {
            PROBLEMS.add("无法完成兼容检查 / Compatibility preflight failed: " + e);
        } finally {
            CLASSES.clear();
            state = PROBLEMS.isEmpty() ? 1 : 2;
            if (!PROBLEMS.isEmpty()) LoggerFactory.getLogger("zifeng_ae2_skills").error(diagnostic());
        }
    }
    /**
     * AE 供电功能的类不参与主契约检查：它单独容错。
     * 否则本体仅改动转换机，就会让四个已稳定的技能一起拒绝加载。
     */
    private static final List<String> AE_POWER_CLASSES = List.of(
            "energy/", "jade/", "mixin/converter/", "mixin/host/ConverterScreenMixin",
            "network/AePowerToggleC2SPacket", "network/AePowerStateS2CPacket", "client/ClientAePower");
    private static boolean aePowerClass(String name) {
        for (String suffix : AE_POWER_CLASSES) if (name.startsWith(OURS + suffix)) return true;
        return false;
    }
    /**
     * 报告兼容性结论。
     *
     * <p>1.20.1 没有 {@code ModLoadingIssue}，{@code ModLoadingException} 的构造签名也完全不同
     * （{@code IModInfo, ModLoadingStage, String, Throwable, Object...}），且它需要加载阶段上下文，
     * 在 {@code FMLCommonSetupEvent} 里自行构造容易得到误导性的崩溃报告。
     * 因此这里改为<b>优雅降级</b>：只记一条 ERROR 级诊断，禁用全部附属功能，让游戏正常进入。
     * 这与 {@link #enabled()} 为 false 时 Mixin 插件不注入、{@code Addon} 提前返回的行为一致。
     */
    public static boolean report() {
        check();
        if (PROBLEMS.isEmpty()) return true;
        LoggerFactory.getLogger("zifeng_ae2_skills").error(diagnostic());
        return false;
    }

    // ------------------------------------------------------------------ 半应用降级
    //
    // 1.2.1 起主配置的注入失败不再让游戏崩（见 MixinFailureHandler），代价是可能出现
    // 「一部分 mixin 已应用、另一部分没应用」的半应用状态。此时若照常跑功能代码，
    // 第一个跨 mixin 的强制转换就会抛 ClassCastException，比原来的启动崩溃更难排查。
    //
    // 因此这里维护一个降级集合，并在 FMLCommonSetupEvent 阶段用**能力检测**确认真实的
    // 应用结果：mixin 加上的接口会出现在已加载的目标类上，用 isAssignableFrom 即可判断。
    // 一旦发现缺失，{@link #degraded()} 为真，{@code Ae2Effects} 会把全部技能等级发布成 0，
    // 所有功能路径在强制转换之前就返回，游戏正常可玩、只是技能不生效。

    /** 降级原因（注入失败 + 能力缺失），为空表示一切正常。 */
    private static final Set<String> DEGRADED = new LinkedHashSet<>();
    private static boolean capabilitiesChecked;

    /** 主配置注入失败：只降级，不崩。由 {@link MixinFailureHandler} 调用。 */
    public static synchronized void markDegraded(String reason) {
        if (reason != null && !reason.isBlank() && DEGRADED.add(reason)) {
            LoggerFactory.getLogger("zifeng_ae2_skills").warn(
                    "AE2 技能功能已降级（游戏继续运行）/ Addon features degraded, game keeps running: {}", reason);
        }
    }

    /** 是否有任何功能因注入失败或缺席而被降级。 */
    public static boolean degraded() { return !DEGRADED.isEmpty(); }

    /** 预检问题清单（不含版本等噪音），供游戏内提示使用。 */
    public static java.util.List<String> problems() { return new java.util.ArrayList<>(PROBLEMS); }

    /** 降级原因的对外文本；未降级时返回 null。 */
    public static String degradedReasons() {
        return DEGRADED.isEmpty() ? null : String.join("；", DEGRADED);
    }

    /**
     * 能力检测：确认每个跨 mixin 接口真的挂到了目标类上。
     * <p>必须在 mixin 应用之后调用（{@code FMLCommonSetupEvent}）；重复调用是安全的。
     * <p>本方法会<b>主动触发</b>目标类加载，加载时 Mixin 才会去应用对应的混入，
     * 因此这里读到的是最终状态，而不是「还没被处理」的原始类。
     */
    public static synchronized void checkApplied() {
        if (capabilitiesChecked) return;
        capabilitiesChecked = true;
        // 预检已经判定不可用时，混入是被插件主动跳过的，不是「被谁抢走」。
        // 那种情况由 report()/diagnostic() 统一说明，这里不必再补 5 条「未接入」噪音。
        if (!enabled()) return;
        require("合成 CPU 集群 / Crafting CPU cluster", CpuExtension.class, CraftingCPUCluster.class);
        require("合成 CPU 逻辑 / Crafting CPU logic", CpuLogicAccess.class, CraftingCpuLogic.class);
        require("合成任务 / Crafting job", JobAccess.class, ExecutingCraftingJob.class);
        require("合成计时器 / Crafting timer", TimeAccess.class, ElapsedTimeTracker.class);
        require("瞬成光标 / Instant crafting cursor", InstantCpuExtension.class, CraftingCpuLogic.class);
        if (degraded()) {
            LoggerFactory.getLogger("zifeng_ae2_skills").error(
                    "AE2 技能因为缺少混入目标而降级，全部技能已停用 / Addon degraded, all skills disabled: {}",
                    degradedReasons());
        }
    }

    private static void require(String label, Class<?> contract, Class<?> target) {
        try {
            if (!contract.isAssignableFrom(target)) markDegraded(label + " 未接入 / not applied");
        } catch (LinkageError | RuntimeException error) {
            markDegraded(label + " 无法加载 / cannot load");
        }
    }
    public static String diagnostic() {
        return "子枫 AE2 技能附属无法加载 / Zifeng AE2 Skills cannot load.\n" + versions + "\n"
                + String.join("\n", PROBLEMS) + "\n请使用兼容的原作者技能树与 AE2，或更新附属；已验证基准：技能树 1.5.0 / AE2 19.2.17 / MC 1.21.1 NeoForge。"
                + "\nUse compatible original mods or update this addon. No addon features were enabled. Details: logs/latest.log";
    }

    /**
     * Validate optional adapters only when their owning mod is installed; never load its classes.
     *
     * <p>1.20.1 起**降级为只记日志、不抛异常**：四个适配模组（AdvancedAE / ExtendedAE /
     * ExtendedAE Plus / UFO）的 1.20.1 版本未经验证，若这里照旧抛 IllegalStateException，
     * 玩家装了任一该模组的一个新版本就会直接开不了游戏。现在最坏结果只是对应 mixin 不生效，
     * 四个 AE 技能与主功能不受影响。诊断信息仍然写进日志供排查。
     *
     * <p>注意：必须清掉本方法自己产生的 PROBLEMS，否则会污染 {@link #enabled()} 的判断
     * （主配置的 shouldApplyMixin 依赖它），把「某个可选适配不匹配」放大成「整个附属禁用」。
     */
    public static synchronized void checkOptionalMixins(String configName) {
        if (!enabled()) return;
        Set<String> optionalProblems = new LinkedHashSet<>();
        try {
            var config = JsonParser.parseString(new String(resource(configName), StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = config.get("package").getAsString().replace('.', '/') + "/";
            for (var entry : config.getAsJsonArray("mixins")) {
                String name = entry.getAsString();
                if (!OptionalCompatibilityPlugin.installedFor(name)) continue;
                var mixin = read(pkg + name.replace('.', '/'));
                checkMixin(mixin);
                checkCalls(mixin);
            }
            optionalProblems.addAll(PROBLEMS);
            if (!optionalProblems.isEmpty()) throw new IllegalStateException(diagnostic());
        } catch (IOException | RuntimeException error) {
            LoggerFactory.getLogger("zifeng_ae2_skills").warn(
                    "AE2 技能第三方适配结构不匹配，将跳过该适配（主功能不受影响）。请更新附属或对应模组 / "
                            + "Optional AE2 skill integration contract failed, adapter skipped (core features unaffected): {}; {}",
                    configName, versions, error);
        } finally {
            // 关键：可选适配的问题不能留在全局 PROBLEMS 里，否则 enabled() 变成 false，
            // 主配置的 19 个 mixin 会被 CompatibilityPlugin 全部跳过。
            PROBLEMS.clear();
            CLASSES.clear();
        }
    }
    private static byte[] resource(String name) throws IOException {
        var path = LoadingModList.get().findResource(name);
        if (path != null && Files.isRegularFile(path)) return Files.readAllBytes(path);
        try (var input = MixinService.getService().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing resource: " + name);
            return input.readAllBytes();
        }
    }
    private static ClassNode read(String name) throws IOException {
        var cached = CLASSES.get(name);
        if (cached != null) return cached;
        var node = new ClassNode();
        new ClassReader(resource(name + ".class")).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        CLASSES.put(name, node);
        return node;
    }
    private static Object annotationValue(AnnotationNode node, String key) {
        if (node == null || node.values == null) return null;
        for (int i = 0; i < node.values.size(); i += 2) if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var all = new ArrayList<AnnotationNode>();
        if (visible != null) all.addAll(visible);
        if (invisible != null) all.addAll(invisible);
        return all;
    }
    private static boolean hasAnnotation(List<AnnotationNode> annotations, String suffix) {
        return annotations.stream().anyMatch(a -> a.desc.endsWith("/" + suffix + ";"));
    }
    private static void checkMixin(ClassNode mixin) throws IOException {
        var annotations = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations);
        var definition = annotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        List<String> targets = new ArrayList<>();
        if (annotationValue(definition, "value") instanceof List<?> values) for (Object v : values) targets.add(((Type) v).getInternalName());
        if (annotationValue(definition, "targets") instanceof List<?> values) for (Object v : values) targets.add(v.toString().replace('.', '/'));
        for (String target : targets) {
            var node = read(target);
            for (var field : mixin.fields) {
                if (hasAnnotation(annotations(field.visibleAnnotations, field.invisibleAnnotations), "Shadow"))
                    requireField(target, field.name, field.desc);
            }
            for (var method : mixin.methods) {
                for (var ann : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                    if (ann.desc.endsWith("/Shadow;")) requireMethod(target, method.name, method.desc);
                    if (ann.desc.endsWith("/Accessor;")) {
                        String name = (String) annotationValue(ann, "value");
                        Type fieldType = Type.getReturnType(method.desc);
                        if (fieldType.equals(Type.VOID_TYPE)) fieldType = Type.getArgumentTypes(method.desc)[0];
                        requireField(target, name, fieldType.getDescriptor());
                    }
                    if (ann.desc.endsWith("/Invoker;")) requireMethod(target, (String) annotationValue(ann, "value"), method.desc);
                    if (annotationValue(ann, "method") instanceof List<?> selectors) {
                        // require=0 是可选项：宿主 1.5.5 才有的入口在 1.5.0 上必然缺席，Mixin 自身容忍这种缺席。
                        // 预检若把它当缺失目标，就会在本来能正常工作的宿主上拒绝加载整个附属。
                        // require=0 marks an optional injection; Mixin itself tolerates an absent target.
                        boolean optional = Integer.valueOf(0).equals(annotationValue(ann, "require"));
                        for (Object selector : selectors) {
                            String s = selector.toString();
                            int desc = s.indexOf('(');
                            if (desc >= 0) {
                                if (!optional) requireMethod(target, s.substring(0, desc), s.substring(desc));
                            } else if (!optional && node.methods.stream().noneMatch(m -> m.name.equals(s))) {
                                PROBLEMS.add("缺少混入目标 / Missing hook: " + target + "." + s);
                            }
                        }
                        Object atValue = annotationValue(ann, "at");
                        List<?> ats = atValue instanceof List<?> list ? list : atValue == null ? List.of() : List.of(atValue);
                        for (Object value : ats) {
                            var at = (AnnotationNode) value;
                            if (!"INVOKE".equals(annotationValue(at, "value"))) continue;
                            String expected = (String) annotationValue(at, "target");
                            int matches = 0;
                            for (var candidate : node.methods) {
                                if (selectors.stream().noneMatch(s -> s.equals(candidate.name) || s.equals(candidate.name + candidate.desc))) continue;
                                for (var instruction : candidate.instructions) {
                                    if (instruction instanceof MethodInsnNode call && expected.equals("L" + call.owner + ";" + call.name + call.desc)) matches++;
                                }
                            }
                            int ordinal = annotationValue(at, "ordinal") instanceof Integer i ? Math.max(0, i) : 0;
                            if (matches <= ordinal && !optional) PROBLEMS.add("缺少注入位置 / Missing injection site: " + target + " -> " + expected);
                        }
                    }
                }
            }
        }
    }
    private static boolean external(String owner) {
        return owner.startsWith("appeng/") || owner.startsWith(HOST)
                || owner.startsWith("net/pedroksl/advanced_ae/");
    }
    private static void checkCalls(ClassNode caller) throws IOException {
        for (var method : caller.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && external(call.owner)) requireMethod(call.owner, call.name, call.desc);
            else if (instruction instanceof FieldInsnNode field && external(field.owner)) requireField(field.owner, field.name, field.desc);
            else if (instruction instanceof TypeInsnNode type && external(type.desc)) read(type.desc);
        }
    }
    private static boolean member(String owner, String name, String desc, boolean field, Set<String> visited) throws IOException {
        if (owner == null || owner.equals("java/lang/Object") || !visited.add(owner)) return false;
        var node = read(owner);
        if (field ? node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))
                : node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) return true;
        if (name.equals("<init>")) return false;
        if (member(node.superName, name, desc, field, visited)) return true;
        for (String iface : node.interfaces) if (member(iface, name, desc, field, visited)) return true;
        return false;
    }
    private static void requireMethod(String owner, String name, String desc) throws IOException {
        if (!member(owner, name, desc, false, new HashSet<>())) PROBLEMS.add("缺少方法 / Missing method: " + owner + "." + name + desc);
    }
    private static void requireField(String owner, String name, String desc) throws IOException {
        if (!member(owner, name, desc, true, new HashSet<>())) PROBLEMS.add("缺少字段 / Missing field: " + owner + "." + name + " " + desc);
    }
}
