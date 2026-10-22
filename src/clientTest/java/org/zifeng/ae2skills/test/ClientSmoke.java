package org.zifeng.ae2skills.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import appeng.api.config.CpuSelectionMode;
import appeng.client.Point;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuList;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.StatePayload;
import org.zifeng.ae2skills.client.ClientEffects;
import org.zifeng.ae2skills.client.CpuStorageDisplay;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;

/** Opt-in actual client boot, mixin application and UI data checks; never shipped. */
@EventBusSubscriber(modid = Addon.MOD_ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static boolean checked;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (checked || !(minecraft.screen instanceof TitleScreen)) return;
        checked = true;
        try {
            Class.forName("appeng.client.gui.widgets.CPUSelectionList");
            Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen");
            checkCpuList(minecraft);
            checkStorageFormatterFallback();
            checkConverterScreenMixinApplied();
            var screen = new SkillTreeScreen(1000000, Map.of("ae_cpu_infinite", 5), Map.of(), Map.of(), true, Map.of());
            Method canLearn = SkillTreeScreen.class.getDeclaredMethod("canLearn", String.class);
            canLearn.setAccessible(true);
            if ((Boolean) canLearn.invoke(screen, "ae_cpu_infinite")) throw new AssertionError("Client must cap learned level");
            Method effect = SkillTreeScreen.class.getDeclaredMethod("skillAttrText", String.class, int.class, boolean.class);
            effect.setAccessible(true);
            if (!effect.invoke(screen, "ae_cpu_infinite", 5, false).equals("∞")) throw new AssertionError("Client must show infinite split effect");
            ClientEffects.apply(new StatePayload(4, 4, 5, 5));
            if (!ClientEffects.value("ae_cpu_infinite", ClientEffects.level("ae_cpu_infinite")).equals("∞")) throw new AssertionError("Global state sync display");
            org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").info("ADDON CLIENT SMOKE PASSED: CPU list rendered and tooltips checked; host UI and AE2 client mixins loaded");
        } catch (Throwable error) {
            org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").error("ADDON CLIENT SMOKE FAILED", error);
        } finally { minecraft.stop(); }
    }

    /**
     * 「使用 AE 能量」按钮是否真的会出现在转换机界面里。
     *
     * <p>这条断言针对一个已发生过的静默故障：mixin 里 {@code @Shadow} 了目标类**未声明**的继承字段
     * {@code font}（它在父类 {@code Screen} 上），导致 mixin 整体应用失败。游戏不崩、日志只有一条 WARN，
     * 唯一表现是界面里根本没有 AE 按钮——构建与其余功能全部正常，极易漏过。
     * 注入进目标类的方法名是「应用成功」的直接证据。
     */
    private static void checkConverterScreenMixinApplied() throws ReflectiveOperationException {
        String target = "org.zifeng.skilltree.client.screen.SkillPointConverterScreen";
        Class<?> screen = Class.forName(target);
        // Mixin 会把 @Inject 的方法改名注入（形如 handler$zzb000$ae2skills$drawAeButton），
        // 因此用「包含」而不是「等于」匹配，并把实际名字打进日志作为证据。
        java.util.List<String> names = java.util.Arrays.stream(screen.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(n -> n.contains("ae2skills$"))
                .sorted()
                .toList();
        org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").info(
                "ADDON CONVERTER MIXIN METHODS on " + target + ": " + names);
        boolean draw = names.stream().anyMatch(n -> n.contains("ae2skills$drawAeButton"));
        boolean click = names.stream().anyMatch(n -> n.contains("ae2skills$clickAeButton"));
        if (!draw || !click) {
            throw new AssertionError("ConverterScreenMixin 未应用到 " + target
                    + "（AE 按钮不会出现）：draw=" + draw + " click=" + click + " names=" + names);
        }
        // 方块实体侧同样断言：AE 取电与 NBT 开关都在这个 mixin 里
        String beTarget = "org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity";
        Class<?> be = Class.forName(beTarget);
        boolean save = java.util.Arrays.stream(be.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("ae2skills$saveAe"));
        if (!save) throw new AssertionError("ConverterBlockEntityMixin 未应用到 " + beTarget);
        org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").info(
                "ADDON CONVERTER MIXIN CHECK PASSED: AE 按钮与 AE 取电 mixin 均已应用到本体目标类");
    }
    private static void checkCpuList(Minecraft minecraft) throws ReflectiveOperationException {
        // The title screen has no player/network menu. Supply only the CPU list consumed by the
        // real widget; all widget construction, rendering, formatting and hit testing run normally.
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        var field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var menu = (CraftingStatusMenu) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), CraftingStatusMenu.class);
        var style = StyleManager.loadStyleDoc("/screens/crafting_status.json");
        var widget = new CPUSelectionList(menu, new Scrollbar(), style);
        widget.setPosition(new Point(0, 0));
        widget.setSize(100, 160);
        var graphics = new CapturingGraphics(minecraft);
        for (int count : new int[]{0, 1, 8, 10_000, Integer.MAX_VALUE - 1}) {
            menu.cpuList = new CraftingCpuList(List.of(new CraftingCpuListEntry(1, 4096, count,
                    Component.literal("CPU regression"), CpuSelectionMode.ANY, null, 0, 0)));
            widget.updateBeforeRender();
            graphics.text.clear();
            widget.drawBackgroundLayer(graphics, new Rect2i(0, 0, 100, 160), new Point(10, 20));
            graphics.flush();
            var tooltip = widget.getTooltip(10, 20);
            if (tooltip == null) throw new AssertionError("CPU hit testing must return a tooltip");
            String hovered = tooltip.getContent().stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
            boolean infinite = count == Integer.MAX_VALUE - 1;
            if (graphics.text.contains("∞") != infinite || hovered.contains("∞") != infinite)
                throw new AssertionError("CPU infinity render/tooltip mismatch at " + count + ": " + graphics.text + hovered);
            if (count > 0 && !infinite) {
                String expected = Integer.toString(count);
                String expectedTooltip = appeng.core.localization.Tooltips.ofNumber(count).getString();
                if (net.neoforged.fml.ModList.get().isLoaded("ufo")) {
                    expected = (String) Class.forName("com.raishxn.ufo.client.CpuAmountFormatter")
                            .getMethod("threads", long.class).invoke(null, (long) count);
                    expectedTooltip = expected;
                }
                if (!graphics.text.contains(expected) || !hovered.contains(expectedTooltip))
                    throw new AssertionError("Finite CPU formatting must be preserved: " + expected + " in " + graphics.text + hovered);
            }
        }
        for (long bytes : new long[]{0, 4096, 1_099_511_627_775_999L,
                1_099_511_627_776_000L, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            menu.cpuList = new CraftingCpuList(List.of(new CraftingCpuListEntry(1, bytes, 8,
                    Component.literal("Storage regression"), CpuSelectionMode.ANY, null, 0, 0)));
            widget.updateBeforeRender();
            graphics.text.clear();
            widget.drawBackgroundLayer(graphics, new Rect2i(0, 0, 100, 160), new Point(10, 20));
            graphics.flush();
            var tooltip = widget.getTooltip(10, 20);
            if (tooltip == null) throw new AssertionError("Storage tooltip missing at " + bytes);
            String hovered = tooltip.getContent().stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
            boolean infinite = bytes == Long.MAX_VALUE;
            boolean visibleInfinite = infinite;
            if (!infinite && net.neoforged.fml.ModList.get().isLoaded("ufo")) {
                String original = (String) Class.forName("com.raishxn.ufo.client.CpuAmountFormatter")
                        .getMethod("storage", long.class).invoke(null, bytes);
                visibleInfinite = original.contains("∞");
                if (!graphics.text.contains(original)) throw new AssertionError("UFO finite list format changed: " + bytes);
            }
            if (graphics.text.contains("∞") != visibleInfinite || hovered.contains("∞") != infinite)
                throw new AssertionError("Storage infinity render/tooltip mismatch at " + bytes + ": " + graphics.text + hovered);
            if (!infinite) {
                String expected = bytes >= 1_099_511_627_776_000L ? bytes + " B"
                        : appeng.core.localization.Tooltips.ofBytes(bytes).getString();
                if (bytes < 1_099_511_627_776_000L && net.neoforged.fml.ModList.get().isLoaded("ufo")) {
                    expected = (String) Class.forName("com.raishxn.ufo.client.CpuAmountFormatter")
                            .getMethod("storage", long.class).invoke(null, bytes);
                }
                if (!hovered.contains(expected)) throw new AssertionError("Finite storage changed: " + bytes + hovered);
            }
        }
        org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").info(
                "ADDON STORAGE WIDGET PASSED: normal, boundary, huge finite and infinite capacity");
    }

    private static void checkStorageFormatterFallback() {
        Component unchanged = CpuStorageDisplay.tooltip(4096, bytes -> Component.literal("custom finite format"));
        if (!unchanged.getString().equals("custom finite format")) throw new AssertionError("Custom formatter changed");
        for (int attempt = 0; attempt < 2; attempt++) {
            Component fallback = CpuStorageDisplay.tooltip(4096, bytes -> {
                throw new IllegalStateException("Intentional storage formatter regression fault");
            });
            if (!fallback.getString().contains("4096 B") || !fallback.getString().contains("logs/latest.log"))
                throw new AssertionError("Missing raw capacity or friendly error: " + fallback.getString());
        }
        for (long bytes : new long[]{1_099_511_627_776_000L, Long.MAX_VALUE}) {
            CpuStorageDisplay.tooltip(bytes, amount -> {
                throw new AssertionError("Unsafe capacity must bypass original formatter");
            });
        }
        try {
            CpuStorageDisplay.tooltip(4096, bytes -> { throw new AssertionError("fatal error sentinel"); });
            throw new IllegalStateException("Fatal errors must not be swallowed");
        } catch (AssertionError expected) {
            if (!expected.getMessage().equals("fatal error sentinel")) throw expected;
        }
        org.slf4j.LoggerFactory.getLogger("zifeng_ae2_skills").info(
                "ADDON STORAGE FALLBACK PASSED: custom format preserved, repeated faults recovered, unsafe formatter bypassed");
    }
    private static final class CapturingGraphics extends GuiGraphics {
        final List<String> text = new ArrayList<>();
        CapturingGraphics(Minecraft minecraft) { super(minecraft, minecraft.renderBuffers().bufferSource()); }
        @Override public int drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
            text.add(value.getString());
            return super.drawString(font, value, x, y, color, shadow);
        }
    }
}
