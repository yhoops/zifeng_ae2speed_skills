package org.zifeng.ae2skills.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
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
    private Compatibility() {}
    public static boolean enabled() { check(); return PROBLEMS.isEmpty(); }
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
    public static boolean report() {
        check();
        if (PROBLEMS.isEmpty()) return true;
        var addon = LoadingModList.get().getMods().stream().filter(m -> m.getModId().equals("zifeng_ae2_skills")).findFirst().orElseThrow();
        throw new ModLoadingException(ModLoadingIssue.error(diagnostic()).withAffectedMod(addon));
    }
    public static String diagnostic() {
        return "子枫 AE2 技能附属无法加载 / Zifeng AE2 Skills cannot load.\n" + versions + "\n"
                + String.join("\n", PROBLEMS) + "\n请使用兼容的原作者技能树与 AE2，或更新附属；已验证基准：技能树 1.5.0 / AE2 19.2.17 / MC 1.21.1 NeoForge。"
                + "\nUse compatible original mods or update this addon. No addon features were enabled. Details: logs/latest.log";
    }

    /** Validate optional adapters only when their owning mod is installed; never load its classes. */
    public static synchronized void checkOptionalMixins(String configName) {
        if (!enabled()) return;
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
            if (!PROBLEMS.isEmpty()) throw new IllegalStateException(diagnostic());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("AE2 技能第三方适配结构不匹配，请更新附属或对应模组 / "
                    + "Optional AE2 skill integration contract failed: " + configName + "; " + versions, error);
        } finally {
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
