package org.zifeng.ae2skills.mixin.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.zifeng.ae2skills.crafting.AssemblerOverclock;

/** Both monitored and unmonitored AE2 tick-manager paths call the same interface. */
@Mixin(value = TickManagerService.class, remap = false)
public abstract class AssemblerTickMixin {
    @Redirect(method = "unsafeTickingRequest", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/ticking/IGridTickable;tickingRequest(Lappeng/api/networking/IGridNode;I)Lappeng/api/networking/ticking/TickRateModulation;"), require = 2)
    private TickRateModulation zifeng$overclock(IGridTickable tickable, IGridNode node, int elapsed) {
        return AssemblerOverclock.tick(tickable, node, elapsed);
    }
}
