package org.zifeng.ae2skills.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.StatePayload;

@EventBusSubscriber(modid = Addon.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEffects.apply(new StatePayload(0, 0, 0, 0));
        ClientAePower.reset();
    }
}
