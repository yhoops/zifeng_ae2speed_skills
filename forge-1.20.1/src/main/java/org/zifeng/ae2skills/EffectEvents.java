package org.zifeng.ae2skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.zifeng.ae2skills.advancement.DivineAdvancements;
import org.zifeng.ae2skills.divine.DivineThunder;
import org.zifeng.ae2skills.network.NetworkHandler;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

/**
 * 1.20.1：{@code @EventBusSubscriber} 来自 {@code net.minecraftforge.fml.common.Mod.EventBusSubscriber}，
 * 并且必须显式声明 {@code bus = Bus.FORGE}（1.20.1 的事件总线是分开的两条）。
 * 事件类型也从 1.21.1 的 {@code ServerTickEvent.Pre/Post}、{@code PlayerTickEvent.Post}
 * 换成 {@code TickEvent.ServerTickEvent}/{@code TickEvent.PlayerTickEvent} + {@code Phase}。
 */
@Mod.EventBusSubscriber(modid = Addon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EffectEvents {
    private EffectEvents() {}

    private static int active(PlayerSkillRecord record, String id) {
        return record.getLearnedPoints(id) > 0 && record.isEnabled(id) ? record.getActiveLevel(id) : 0;
    }

    public static void update(ServerPlayer player) {
        var record = PlayerSkillSavedData.get(player.serverLevel()).getPlayer(player.getUUID());
        if (record == null) { Ae2Effects.remove(player.getUUID()); return; }
        int divine = active(record, SkillDefinitions.DIVINE_MACHINE);
        Ae2Effects.update(player.getUUID(), active(record, SkillDefinitions.ASSEMBLER_SPEED),
                active(record, SkillDefinitions.CPU_SPEED), active(record, SkillDefinitions.ASSEMBLER_INFINITE),
                active(record, SkillDefinitions.CPU_INFINITE), divine);
        // 成就只在存档内第一次把该技能真正激活到对应门槛时发放一次；其余等级不做任何事。
        // 万机之神有 5 级 / 6 级两道门槛（5 级门槛钉成常量，不随上限变化），另外两个技能只有满级一道。
        for (String id : SkillDefinitions.DIVINE_TAB_SKILLS) {
            int level = SkillDefinitions.DIVINE_MACHINE.equals(id) ? divine : active(record, id);
            if (DivineAdvancements.grant(player, id, level) && SkillDefinitions.DIVINE_MACHINE.equals(id)) {
                // 只有第 6 级这道门槛配雷声；5 级成就保持 1.1.7 的原样体验。
                if (level >= SkillDefinitions.DIVINE_MAX_LEVEL) DivineThunder.announce(player);
            }
        }
    }

    /** 1.21.1 的 {@code ServerTickEvent.Pre} → 1.20.1 的 {@code TickEvent.ServerTickEvent} + {@code Phase.START}。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void begin(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) TickBudget.markTickStart();
    }

    /** 1.21.1 的 {@code PlayerTickEvent.Post} → 1.20.1 的 {@code TickEvent.PlayerTickEvent} + {@code Phase.END}。 */
    @SubscribeEvent
    public static void player(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player) update(player);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            update(player);
            NetworkHandler.sendToPlayer(player, StatePayload.current());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { Ae2Effects.remove(event.getEntity().getUUID()); }

    /** 1.21.1 的 {@code ServerTickEvent.Post} → 1.20.1 的 {@code Phase.END}。 */
    @SubscribeEvent
    public static void end(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Ae2Effects.dirty) {
            Ae2Effects.dirty = false;
            NetworkHandler.sendToAllPlayers(StatePayload.current());
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        Ae2Effects.clear();
        TickBudget.reset();
        // 换存档/重进服务器时必须重新允许发放（判定依据在存档里，这里只清会话缓存）。
        DivineAdvancements.resetCache();
    }
}
