package org.zifeng.ae2skills.mixin.ae2;

import appeng.client.gui.widgets.CPUSelectionList;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.zifeng.ae2skills.client.CpuStorageDisplay;

@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class CpuMenuMixin {
    // Intercept before AE2's byte formatter can index beyond its unit table.
    @WrapOperation(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/Tooltips;ofBytes(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent addon$storageTooltip(long bytes, Operation<MutableComponent> original) {
        return CpuStorageDisplay.tooltip(bytes, amount -> original.call(amount));
    }

    // 1.20.1：InfoBar.add 只有 (String,int,float) 三参，1.21.1 多出的两个 int 参数在这里不存在
    @ModifyArg(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IF)V", ordinal = 1), index = 0)
    private String addon$storageInfinity(String formatted, @Local CraftingCpuListEntry cpu) {
        return cpu.storage() == Long.MAX_VALUE ? "∞" : formatted;
    }

    // Modify the displayed value after number formatters (including UFO's redirects) have run.
    // The three String InfoBar entries are crafting amount, storage and co-processors.
    @ModifyArg(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IF)V", ordinal = 2), index = 0)
    private String addon$infinity(String formatted, @Local CraftingCpuListEntry cpu) {
        return cpu.coProcessors() == Integer.MAX_VALUE - 1 ? "∞" : formatted;
    }
    @ModifyArg(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/ButtonToolTips;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
            // 1.20.1 的 getTooltip 字节码顺序与 1.21.1 相反：CpuStatusStorage 在 offset 40（前），
            // CpuStatusCoProcessor 在 offset 84（后）。原 slice 的 from/to 在此是倒序 -> 找不到 to -> 注入失败，
            // 而本 config 是 required:true，失败会直接崩客户端。故改为 CoProcessor -> CoProcessors。
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lappeng/core/localization/ButtonToolTips;CpuStatusCoProcessor:Lappeng/core/localization/ButtonToolTips;"),
                    to = @At(value = "FIELD", target = "Lappeng/core/localization/ButtonToolTips;CpuStatusCoProcessors:Lappeng/core/localization/ButtonToolTips;")), index = 0)
    private Object[] addon$tooltip(Object[] formatted, @Local CraftingCpuListEntry cpu) {
        if (cpu.coProcessors() != Integer.MAX_VALUE - 1) return formatted;
        Object[] result = java.util.Arrays.copyOf(formatted, formatted.length);
        result[0] = Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT);
        return result;
    }
}
