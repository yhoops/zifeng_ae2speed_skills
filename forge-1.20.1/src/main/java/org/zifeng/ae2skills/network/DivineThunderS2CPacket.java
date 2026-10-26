package org.zifeng.ae2skills.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.ae2skills.client.ClientDivineThunder;

/**
 * 满级成就的雷声（服务端 → 达成者客户端）。
 *
 * <p>空载荷：位置与音色都由客户端本地的玩家自己决定，服务端不需要传任何数据。
 * 这样既不需要新增自定义音效资源，也不会因为坐标/维度差异播到别处。
 *
 * <p>1.20.1：{@code StreamCodec.unit} 不存在，改为无字段的普通消息体。
 */
public record DivineThunderS2CPacket() {
    public static final DivineThunderS2CPacket INSTANCE = new DivineThunderS2CPacket();

    public static void encode(DivineThunderS2CPacket value, FriendlyByteBuf buf) {
        // 空载荷
    }

    public static DivineThunderS2CPacket decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(DivineThunderS2CPacket data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientDivineThunder::accept);
        ctx.get().setPacketHandled(true);
    }
}
