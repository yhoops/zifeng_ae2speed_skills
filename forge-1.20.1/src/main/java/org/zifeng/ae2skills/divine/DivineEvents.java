package org.zifeng.ae2skills.divine;

// 1.20.1：NeoForge 的 net.neoforged.* 换回 Forge 的 net.minecraftforge.*；
// 1.20.1 没有独立的 ServerTickEvent.Pre 类，改用 TickEvent.ServerTickEvent + phase 判定。
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zifeng.ae2skills.Addon;

/** 每个服务器 tick 开头结算「万机之神的加护」。 */
@Mod.EventBusSubscriber(modid = Addon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivineEvents {
    private DivineEvents() {}
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        // 1.20.1：连 START/END 都会派发，只在 START 时结算，语义等同 1.21.1 的 ServerTickEvent.Pre
        if (event.phase != TickEvent.Phase.START) return;
        DivineGrace.tick(event.getServer());
    }
    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        DivineGrace.reset();
        DivineTranscend.reset();
    }
}
