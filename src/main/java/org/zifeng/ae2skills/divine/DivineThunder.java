package org.zifeng.ae2skills.divine;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.zifeng.ae2skills.network.DivineThunderS2CPacket;

/**
 * 满级（第 6 级）成就的一次性演出：只给达成者本人放一道雷。
 *
 * <h2>为什么只给达成者</h2>
 * 成就本身已经开了 {@code announce_to_chat}，全服都能在聊天栏看到公告；
 * 音效如果发给所有人，多人服里一个 OP 点满技能会让别人莫名其妙听到雷。
 * 因此这里只发 S2C 包给达成者，其余玩家只看到公告。
 *
 * <h2>为什么不用 {@code Level#playSound}</h2>
 * 那个入口会按距离衰减，且以「除自己以外的人」为受众语义，客户端判定
 * {@code except == minecraft.player} 时才播放——与「只放给指定玩家」正好相反。
 * 客户端直接调 {@code ClientLevel#playLocalSound} 才能做到无衰减、只此一人。
 */
public final class DivineThunder {
    private DivineThunder() {}

    /** 由成就发放成功的调用点触发；只发一次，不重试。 */
    public static void announce(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return;
        PacketDistributor.sendToPlayer(player, DivineThunderS2CPacket.INSTANCE);
    }
}
