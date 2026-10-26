package org.zifeng.ae2skills.compat;
// 1.20.1：包名由 net.neoforged.fml.loading 改为 net.minecraftforge.fml.loading（getDist() 签名一致）。
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * AE 供电功能的独立闸门。
 *
 * <p>与四技能的主配置不同，本配置刻意 {@code required: false} 并在**契约不匹配时静默跳过**：
 * 该功能默认关闭、不持有存档数据，缺失的唯一后果是退回 FE 供电，
 * 因此不应让四个已稳定的技能随本体的无关改动一起失效。
 */
public final class AePowerMixinPlugin implements IMixinConfigPlugin {
    private static final String HOST = "org/zifeng/skilltree/";

    /**
     * 转换机方块实体：本 mixin 直接依赖的成员（名称 + 描述符）。
     *
     * <p>每一项可以是「一个或多个可接受写法」——因为 1.20.1 Forge 的<b>生产/开发运行期</b>
     * 对原版覆写方法使用 SRG 名（{@code m_183515_}），而 {@code official} 映射下源码里写的是
     * Mojang 名（{@code saveAdditional}）。契约探针读的是运行期 class 字节，
     * 因此两种写法都接受，避免把「名字风格不同」误判成「契约不匹配」而静默禁用功能。
     */
    private static final List<List<String>> CONVERTER_METHODS = List.of(
            List.of("getThreshold()J"),
            List.of("getInputRate()J"),
            List.of("isUnlimitedInput()Z"),
            List.of("receiveEnergyLong(JZ)J"),
            // 1.20.1：能量能力接口在 net.minecraftforge.energy 而非 net.neoforged.neoforge.energy。
            List.of("getEnergyStorage()Lnet/minecraftforge/energy/IEnergyStorage;"),
            List.of("getCapacityLong()J"),
            List.of("isRedstoneBlocked()Z"),
            List.of("getOwnerUUID()Ljava/util/UUID;"),
            List.of("serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lorg/zifeng/skilltree/blockentity/SkillPointConverterBlockEntity;)V"),
            // 1.20.1：saveAdditional/loadAdditional/createMenu 不再收 HolderLookup.Provider。
            List.of("m_183515_(Lnet/minecraft/nbt/CompoundTag;)V",
                    "saveAdditional(Lnet/minecraft/nbt/CompoundTag;)V"),
            List.of("m_142466_(Lnet/minecraft/nbt/CompoundTag;)V",
                    "loadAdditional(Lnet/minecraft/nbt/CompoundTag;)V"),
            List.of("m_7208_(ILnet/minecraft/world/entity/player/Inventory;"
                            + "Lnet/minecraft/world/entity/player/Player;)"
                            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                    "createMenu(ILnet/minecraft/world/entity/player/Inventory;"
                            + "Lnet/minecraft/world/entity/player/Player;)"
                            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"));
    private static final List<List<String>> CONVERTER_FIELDS = List.of(
            List.of("progress J"), List.of("lastReceiveTick J"));

    /** 界面：新增按钮复用本体的手绘范式，需要这些字段与方法。 */
    private static final List<List<String>> SCREEN_METHODS = List.of(
            // 1.20.1：运行期宿主类里这两个覆写方法带 SRG 名；开发期可能是 Mojang 名，两者都接受。
            List.of("m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"),
            List.of("m_6375_(DDI)Z", "mouseClicked(DDI)Z"));
    private static final List<List<String>> SCREEN_FIELDS = List.of(
            List.of("btnX I"), List.of("btnY I"), List.of("btnW I"), List.of("btnH I"));

    /** 菜单：GUI 需要由数据槽还原方块位置。 */
    private static final String MENU_CLASS = HOST + "menu/SkillPointConverterMenu";
    private static final List<List<String>> MENU_METHODS = List.of(
            List.of("getBlockPos()Lnet/minecraft/core/BlockPos;"),
            List.of("getBlockEntity()L" + HOST + "blockentity/SkillPointConverterBlockEntity;"));

    private static Boolean available;
    /** 界面按此方块类型注册 Jade 提示，需确认方块类仍存在且同名。 */
    private static final String BLOCK_CLASS = HOST + "block/SkillPointConverterBlock";

    @Override
    public void onLoad(String pkg) {
        // 判定推迟到 shouldApplyMixin：那时类加载器已就绪
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return converterContractOk();
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    /** 惰性判定一次，结果缓存；失败时只记录一条可读的日志与功能级诊断。 */
    public static synchronized boolean converterContractOk() {
        if (available != null) return available;
        available = false;
        try {
            // 服务端只校验服务端成员；客户端界面类在专用服务器上不存在
            ClassNode converter = read(HOST + "blockentity/SkillPointConverterBlockEntity");
            ClassNode menu = read(MENU_CLASS);
            check(converter, "SkillPointConverterBlockEntity", CONVERTER_METHODS, CONVERTER_FIELDS);
            check(menu, "SkillPointConverterMenu", MENU_METHODS, List.of());
            // Jade 提示按方块类型注册；缺类会在插件注册时抛错，因此一并校验
            read(BLOCK_CLASS);
            if (FMLLoader.getDist().isClient()) {
                ClassNode screen = read(HOST + "client/screen/SkillPointConverterScreen");
                check(screen, "SkillPointConverterScreen", SCREEN_METHODS, SCREEN_FIELDS);
            }
            if (AePowerFeature.enabled()) {
                LoggerFactory.getLogger("zifeng_ae2_skills").info(
                        "[AE 供电] 转换机契约检查通过，已启用「使用 AE 能量」开关。");
            }
            available = true;
        } catch (Exception | LinkageError e) {
            AePowerFeature.problem(String.valueOf(e));
            LoggerFactory.getLogger("zifeng_ae2_skills").warn(AePowerFeature.diagnostic()
                    + " 附属其余功能不受影响 / Other addon features keep working.");
        }
        return available;
    }

    /** 每一项可以有多种可接受写法（SRG 名 / Mojang 名），命中任意一种即通过。 */
    private static void check(ClassNode node, String label, List<List<String>> methods,
                              List<List<String>> fields) {
        for (List<String> alternatives : methods) {
            boolean found = false;
            for (String spec : alternatives) {
                int split = spec.indexOf('(');
                String name = spec.substring(0, split);
                String desc = spec.substring(split);
                if (node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("缺少方法 / Missing method: " + label + "."
                        + String.join(" | ", alternatives));
            }
        }
        for (List<String> alternatives : fields) {
            boolean found = false;
            for (String spec : alternatives) {
                int split = spec.lastIndexOf(' ');
                String name = spec.substring(0, split);
                String desc = spec.substring(split + 1);
                if (node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalStateException("缺少字段 / Missing field: " + label + "."
                        + String.join(" | ", alternatives));
            }
        }
    }

    private static ClassNode read(String name) throws IOException {
        var node = new ClassNode();
        try (var input = MixinService.getService().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IOException("Missing class: " + name);
            new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
}
