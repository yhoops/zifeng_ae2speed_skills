package org.zifeng.ae2skills.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.client.ClientDivineThunder;

/**
 * 满级成就的雷声（服务端 → 达成者客户端）。
 *
 * <p>空载荷：位置与音色都由客户端本地的玩家自己决定，服务端不需要传任何数据。
 * 这样既不需要新增自定义音效资源，也不会因为坐标/维度差异播到别处。
 */
public record DivineThunderS2CPacket() implements CustomPacketPayload {
    public static final DivineThunderS2CPacket INSTANCE = new DivineThunderS2CPacket();
    public static final Type<DivineThunderS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Addon.MOD_ID, "divine_thunder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DivineThunderS2CPacket> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, ClientDivineThunder::accept);
    }
}
