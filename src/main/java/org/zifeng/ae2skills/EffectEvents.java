package org.zifeng.ae2skills;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.ae2skills.advancement.DivineAdvancements;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;

@EventBusSubscriber(modid = Addon.MOD_ID)
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
        // 成就只在存档内第一次把该技能真正激活到满级时发放一次；其余等级不做任何事。
        // 神明之力页签的三个技能各有一个同名成就，满级条件按各自 maxLevel 判定。
        for (String id : SkillDefinitions.DIVINE_TAB_SKILLS) {
            int level = SkillDefinitions.DIVINE_MACHINE.equals(id) ? divine : active(record, id);
            if (level >= SkillDefinitions.maxLevel(id)) DivineAdvancements.grant(player, id);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void begin(ServerTickEvent.Pre event) { TickBudget.markTickStart(); }
    @SubscribeEvent
    public static void player(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player);
    }
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            update(player);
            PacketDistributor.sendToPlayer(player, StatePayload.current());
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { Ae2Effects.remove(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void end(ServerTickEvent.Post event) {
        if (Ae2Effects.dirty) {
            Ae2Effects.dirty = false;
            PacketDistributor.sendToAllPlayers(StatePayload.current());
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
