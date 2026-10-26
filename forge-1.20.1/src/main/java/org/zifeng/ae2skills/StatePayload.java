package org.zifeng.ae2skills;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.ae2skills.client.ClientEffects;

/**
 * 技能等级快照（服务端 → 客户端）。
 *
 * <p>1.20.1：不再实现 {@code CustomPacketPayload}，也去掉 {@code StreamCodec}/{@code Type}；
 * 改为提供静态 {@code encode/decode/handle}，由 {@code NetworkHandler} 统一注册。
 */
public record StatePayload(int assembler, int cpu, int instant, int split, int divine) {
    public static StatePayload current() {
        return new StatePayload(Ae2Effects.getAssemblerSpeedLevel(), Ae2Effects.getCpuSpeedLevel(),
                Ae2Effects.getAssemblerInfiniteLevel(), Ae2Effects.getCpuInfiniteLevel(), Ae2Effects.getDivineLevel());
    }

    public static void encode(StatePayload value, FriendlyByteBuf buf) {
        buf.writeVarInt(value.assembler);
        buf.writeVarInt(value.cpu);
        buf.writeVarInt(value.instant);
        buf.writeVarInt(value.split);
        buf.writeVarInt(value.divine);
    }

    public static StatePayload decode(FriendlyByteBuf buf) {
        return new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StatePayload data, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientEffects.apply(data));
        ctx.get().setPacketHandled(true);
    }
}
