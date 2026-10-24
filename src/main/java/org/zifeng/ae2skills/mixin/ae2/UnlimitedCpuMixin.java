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
import org.zifeng.ae2skills.divine.DivineTranscend;

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
        // 5 级分裂算力不再走原生 executeCrafting，而是自己直连 pushPattern。这里必须补上同一套
        // 「正在推送的 CPU」标记，否则「万机之神的加护」6 级的取证与上锁会在终点玩家身上整体失效。
        DivineTranscend.enterPush(logic);
        try {
            UnlimitedDispatch.tick(logic, addon$dispatch, service, energy);
        } finally {
            DivineTranscend.exitPush();
        }
        return 0;
    }
}
