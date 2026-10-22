package org.zifeng.ae2skills.mixin.ae2;

import java.util.Arrays;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.crafting.UnlimitedDispatch;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class UnlimitedCpuMixin {
    @Shadow @Final private int[] usedOps;
    @Unique private final UnlimitedDispatch.Cursor addon$dispatch = new UnlimitedDispatch.Cursor();
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void addon$resetWindow(IEnergyService energy, CraftingService service, CallbackInfo ci) {
        if (Ae2Effects.getCpuInfiniteLevel() >= 5) Arrays.fill(usedOps, 0);
        else addon$dispatch.clear();
    }
    @Redirect(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"))
    private int addon$safeNativeCounter(CraftingCPUCluster cluster) {
        // The native loop gets one invocation. The bounded dispatcher owns all actual work.
        return Ae2Effects.getCpuInfiniteLevel() >= 5 ? 0 : cluster.getCoProcessors();
    }
    @Redirect(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"))
    private int addon$dispatch(CraftingCpuLogic logic, int quota, CraftingService service, IEnergyService energy, Level level) {
        if (Ae2Effects.getCpuInfiniteLevel() < 5) return logic.executeCrafting(quota, service, energy, level);
        UnlimitedDispatch.tick(logic, addon$dispatch, service, energy);
        return 0;
    }
}
