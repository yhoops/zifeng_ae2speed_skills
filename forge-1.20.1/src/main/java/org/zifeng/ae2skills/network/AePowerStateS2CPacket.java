package org.zifeng.ae2skills.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.ae2skills.client.ClientAePower;

/**
 * AE 供电状态（服务端 → 客户端）：开关与「相邻是否已接入 ME 网络」。
 * 本体没有可用于扩展的客户端同步通道（ContainerData 硬上限 12 槽且为匿名内部类），因此附属自带此包。
 *
 * <p>1.20.1：改用 {@code SimpleChannel} 的静态 {@code encode/decode/handle}。
 * {@code BlockPos.STREAM_CODEC} 在 1.20.1 不存在，这里直接用 {@code FriendlyByteBuf} 的
 * {@code writeBlockPos}/{@code readBlockPos}（1.20.1 自带且语义完全一致）。
 */
public record AePowerStateS2CPacket(BlockPos pos, boolean enabled, boolean connected) {
    public static void encode(AePowerStateS2CPacket value, FriendlyByteBuf buf) {
        buf.writeBlockPos(value.pos);
        buf.writeBoolean(value.enabled);
        buf.writeBoolean(value.connected);
    }

    public static AePowerStateS2CPacket decode(FriendlyByteBuf buf) {
        return new AePowerStateS2CPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(AePowerStateS2CPacket data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientAePower.accept(data));
        ctx.get().setPacketHandled(true);
    }
}
