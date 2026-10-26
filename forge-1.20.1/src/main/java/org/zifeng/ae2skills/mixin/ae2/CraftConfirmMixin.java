package org.zifeng.ae2skills.mixin.ae2;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmMixin {
    @ModifyArg(method = "updateBeforeRender", at = @At(value = "INVOKE", target = "Lappeng/core/localization/GuiText;text([Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"), index = 0)
    private Object[] addon$infinity(Object[] args) {
        if (args.length != 2 || !Integer.valueOf(Integer.MAX_VALUE - 1).equals(args[1])) return args;
        Object[] result = java.util.Arrays.copyOf(args, args.length);
        result[1] = "∞";
        return result;
    }
}
