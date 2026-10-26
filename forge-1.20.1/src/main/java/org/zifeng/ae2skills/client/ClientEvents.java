package org.zifeng.ae2skills.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.StatePayload;

/**
 * 1.20.1：{@code @EventBusSubscriber} 只能写 {@code value = Dist.CLIENT}，
 * 且必须显式 {@code bus = Bus.FORGE}；客户端事件包也从 {@code net.neoforged.neoforge.client.event}
 * 换成 {@code net.minecraftforge.client.event}。
 * 1.21.1 的 {@code ClientTickEvent.Post} → 1.20.1 的 {@code TickEvent.ClientTickEvent} + {@code Phase.END}。
 */
@Mod.EventBusSubscriber(modid = Addon.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEffects.apply(new StatePayload(0, 0, 0, 0, 0));
        ClientAePower.reset();
        ClientDivineThunder.reset();
    }

    /** 满级成就的雷声：包只置一个标记，真正的播放留在客户端专属类里。 */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ClientDivineThunder.consumePending()) DivineThunderPlayer.play();
    }
}
