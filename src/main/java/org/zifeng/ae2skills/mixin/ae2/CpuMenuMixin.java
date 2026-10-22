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

    @ModifyArg(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 1), index = 0)
    private String addon$storageInfinity(String formatted, @Local CraftingCpuListEntry cpu) {
        return cpu.storage() == Long.MAX_VALUE ? "∞" : formatted;
    }

    // Modify the displayed value after number formatters (including UFO's redirects) have run.
    // The three String InfoBar entries are crafting amount, storage and co-processors.
    @ModifyArg(method = "drawBackgroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V", ordinal = 2), index = 0)
    private String addon$infinity(String formatted, @Local CraftingCpuListEntry cpu) {
        return cpu.coProcessors() == Integer.MAX_VALUE - 1 ? "∞" : formatted;
    }
    @ModifyArg(method = "getTooltip", at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/ButtonToolTips;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lappeng/core/localization/ButtonToolTips;CpuStatusCoProcessor:Lappeng/core/localization/ButtonToolTips;"),
                    to = @At(value = "FIELD", target = "Lappeng/core/localization/ButtonToolTips;CpuStatusStorage:Lappeng/core/localization/ButtonToolTips;")), index = 0)
    private Object[] addon$tooltip(Object[] formatted, @Local CraftingCpuListEntry cpu) {
        if (cpu.coProcessors() != Integer.MAX_VALUE - 1) return formatted;
        Object[] result = java.util.Arrays.copyOf(formatted, formatted.length);
        result[0] = Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT);
        return result;
    }
}
