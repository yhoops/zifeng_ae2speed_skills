package org.zifeng.ae2skills;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.zifeng.ae2skills.client.ClientEffects;

public record StatePayload(int assembler, int cpu, int instant, int split) implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Addon.MOD_ID, "levels"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.assembler); buf.writeVarInt(value.cpu); buf.writeVarInt(value.instant); buf.writeVarInt(value.split); },
            buf -> new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    public Type<StatePayload> type() { return TYPE; }
    public static StatePayload current() {
        return new StatePayload(Ae2Effects.getAssemblerSpeedLevel(), Ae2Effects.getCpuSpeedLevel(),
                Ae2Effects.getAssemblerInfiniteLevel(), Ae2Effects.getCpuInfiniteLevel());
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (data, ctx) -> ctx.enqueueWork(() -> ClientEffects.apply(data)));
    }
}
