package org.zifeng.ae2skills.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.client.ClientAePower;

/**
 * AE 供电状态（服务端 → 客户端）：开关与「相邻是否已接入 ME 网络」。
 * 本体没有可用于扩展的客户端同步通道（ContainerData 硬上限 12 槽且为匿名内部类），因此附属自带此包。
 */
public record AePowerStateS2CPacket(BlockPos pos, boolean enabled, boolean connected) implements CustomPacketPayload {
    public static final Type<AePowerStateS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Addon.MOD_ID, "ae_power_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AePowerStateS2CPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AePowerStateS2CPacket::pos,
            ByteBufCodecs.BOOL, AePowerStateS2CPacket::enabled,
            ByteBufCodecs.BOOL, AePowerStateS2CPacket::connected,
            AePowerStateS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (data, ctx) -> ClientAePower.accept(data, ctx));
    }
}
