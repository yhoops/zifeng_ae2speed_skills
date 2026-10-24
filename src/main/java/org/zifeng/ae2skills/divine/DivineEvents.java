package org.zifeng.ae2skills.divine;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.zifeng.ae2skills.Addon;

/** 每个服务器 tick 开头结算「万机之神的加护」。 */
@EventBusSubscriber(modid = Addon.MOD_ID)
public final class DivineEvents {
    private DivineEvents() {}
    @SubscribeEvent
    public static void tick(ServerTickEvent.Pre event) { DivineGrace.tick(event.getServer()); }
    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        DivineGrace.reset();
        DivineTranscend.reset();
    }
}
