package org.zifeng.ae2skills.mixin.host;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.client.ClientAePower;
import org.zifeng.ae2skills.compat.AePowerFeature;
import org.zifeng.ae2skills.network.AePowerToggleC2SPacket;
import org.zifeng.skilltree.client.screen.SkillPointConverterScreen;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

/**
 * 在转换机界面追加第二个开关「使用 AE 能量」。
 *
 * <p>本体既有的「无限制输入」按钮是手绘的（不在 widget 列表里），这里沿用同一手法：
 * 在 {@code renderBg} 结束时自己画一个按钮，并在 {@code mouseClicked} 头部先于本体判定点击。
 * 两种供电模式下按钮都保持可点、不灰化——AE 模式下按它表示解除渐进抽取上限。
 *
 * <p>布局锚点复用本体已算好的 {@code btnX/btnY/btnW/btnH}（无限制按钮），
 * 把 AE 按钮放在它下方，因此本体调整布局时这里会自动跟随。
 */
@Mixin(value = SkillPointConverterScreen.class, remap = false)
public abstract class ConverterScreenMixin {
    @Shadow private int btnX;
    @Shadow private int btnY;
    @Shadow private int btnW;
    @Shadow private int btnH;

    /**
     * 字体走 {@link Minecraft#font}，**不**声明 {@code @Shadow Font font}。
     *
     * <p>本体把 {@code font} 留给父类 {@code Screen} 声明（目标类自己只声明按钮与编辑框那几项），
     * 而 {@code @Shadow} 无法解析目标类未声明的继承字段：写成 {@code @Shadow protected Font font;}
     * 会让**整个 mixin 应用失败**（日志 {@code @Shadow field font was not located in the target class}），
     * 表现就是转换机界面里根本没有 AE 按钮，且不崩游戏、很难察觉。
     * {@code Screen.init()} 本来就是从 {@code Minecraft.font} 赋值的，取值完全等价。
     */
    @Unique
    private static Font ae2skills$font() {
        return Minecraft.getInstance().font;
    }

    /** AE 按钮区域（屏幕坐标，每帧由 renderBg 尾部更新） */
    @Unique private int ae2skills$aeBtnX;
    @Unique private int ae2skills$aeBtnY;
    @Unique private int ae2skills$aeBtnW;
    @Unique private int ae2skills$aeBtnH;
    /** 客户端乐观状态（null = 跟随服务端同步值） */
    @Unique private Boolean ae2skills$localEnabled;
    /** 发起乐观切换时看到的状态版本；服务端回推后即作废乐观值 */
    @Unique private int ae2skills$localRevision = -1;

    /** 本机菜单持有的方块位置；无法取得时返回 null。 */
    @Unique
    private BlockPos ae2skills$pos() {
        if (((Object) this) instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() instanceof SkillPointConverterMenu menu) {
            return menu.getBlockPos();
        }
        return null;
    }

    @Unique
    private boolean ae2skills$currentEnabled() {
        // 服务端回推过新状态即放弃乐观值，避免非主人点击后停在错值
        int revision = ClientAePower.revision();
        if (ae2skills$localEnabled != null && ae2skills$localRevision != revision) {
            ae2skills$localEnabled = null;
        }
        if (ae2skills$localEnabled != null) return ae2skills$localEnabled;
        BlockPos pos = ae2skills$pos();
        return pos != null && ClientAePower.enabled(pos);
    }

    @Unique
    private static String ae2skills$t(String key) {
        return Component.translatable("ui.zifeng_ae2_skills." + key).getString();
    }

    /** 追加按钮：放在本体无限制按钮的正下方（速率文字之下）。 */
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void ae2skills$drawAeButton(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (!AePowerFeature.enabled()) return;
        ae2skills$aeBtnX = btnX;
        ae2skills$aeBtnY = btnY + btnH + 17; // 无限制按钮 + 其下方的速率文字
        ae2skills$aeBtnW = btnW;
        ae2skills$aeBtnH = 16;

        Font font = ae2skills$font();
        boolean enabled = ae2skills$currentEnabled();
        int bg = enabled ? 0xFF1E5A7A : 0xFF3A3A3A;
        int border = enabled ? 0xFF55CCFF : 0xFF888888;
        int x = ae2skills$aeBtnX, y = ae2skills$aeBtnY, w = ae2skills$aeBtnW, h = ae2skills$aeBtnH;
        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);
        guiGraphics.fill(x + 4, y + 6, x + 9, y + 11, enabled ? 0xFF55CCFF : 0xFF666666);
        guiGraphics.drawString(font, ae2skills$t(enabled ? "ae_on" : "ae_off"), x + 13, y + 3,
                enabled ? 0xFF55CCFF : 0xFFAAAAAA, false);

        // 已启用但相邻没有 ME 网络 → 就地在按钮下方说明原因（用户不必查日志）
        BlockPos pos = ae2skills$pos();
        if (enabled && (pos == null || !ClientAePower.connected(pos))) {
            guiGraphics.drawString(font, ae2skills$t("ae_not_connected"), x, y + h + 3, 0xFFFF5555, false);
        }
    }

    /**
     * 点击 AE 按钮 → 本地乐观翻转 + 发送切换包（服务端只允许主人修改）。
     *
     * <p>与本体自己的「无限制输入」按钮一样消费这次点击（{@code return true} 先于 super），
     * 因此点击本按钮不会同时触发本体的「点编辑框外 → 提交速率」逻辑——这是本体的既有风格。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2skills$clickAeButton(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!AePowerFeature.enabled() || button != 0) return;
        if (mouseX < ae2skills$aeBtnX || mouseX > ae2skills$aeBtnX + ae2skills$aeBtnW) return;
        if (mouseY < ae2skills$aeBtnY || mouseY > ae2skills$aeBtnY + ae2skills$aeBtnH) return;
        boolean next = !ae2skills$currentEnabled();
        ae2skills$localEnabled = next;
        ae2skills$localRevision = ClientAePower.revision();
        BlockPos pos = ae2skills$pos();
        if (pos != null) PacketDistributor.sendToServer(new AePowerToggleC2SPacket(pos, next));
        cir.setReturnValue(true);
    }
}
