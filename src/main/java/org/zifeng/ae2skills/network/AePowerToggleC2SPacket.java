package org.zifeng.ae2skills.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.compat.AePowerFeature;
import org.zifeng.ae2skills.energy.AePowerHolder;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

/**
 * 「使用 AE 能量」开关（客户端 → 服务端）。
 *
 * <p>与本体既有的 ConverterUnlimitedC2SPacket 不同，本开关**只允许机器主人切换**：
 * 无主人的机器一律拒绝（那种机器本来就产不出技能点，拒绝不损失功能，还避免了白抽电网）。
 */
public record AePowerToggleC2SPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {
    public static final Type<AePowerToggleC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Addon.MOD_ID, "ae_power_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AePowerToggleC2SPacket> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AePowerToggleC2SPacket::pos,
            ByteBufCodecs.BOOL, AePowerToggleC2SPacket::enabled,
            AePowerToggleC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, AePowerToggleC2SPacket::handle);
    }

    public static void handle(AePowerToggleC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.flow().isServerbound() || !(ctx.player() instanceof ServerPlayer player)) return;
            SkillPointConverterBlockEntity converter = null;
            if (player.containerMenu instanceof SkillPointConverterMenu menu) {
                converter = menu.getBlockEntity();
            }
            if (converter == null) {
                // 仍回推权威状态，避免客户端停在乐观值
                PacketDistributor.sendToPlayer(player, new AePowerStateS2CPacket(packet.pos(), false, false));
                return;
            }
            BlockPos actualPos = converter.getBlockPos();
            if (!actualPos.equals(packet.pos())) {
                boolean actual = converter instanceof AePowerHolder holder && holder.ae2skills$isAePowerEnabled();
                boolean connected = converter instanceof AePowerHolder holder2
                        && holder2.ae2skills$isAeNetworkConnected();
                PacketDistributor.sendToPlayer(player, new AePowerStateS2CPacket(actualPos, actual, connected));
                return;
            }
            if (!AePowerFeature.enabled()) return;
            java.util.UUID owner = converter.getOwnerUUID();
            boolean allowed = owner != null && owner.equals(player.getUUID())
                    && converter instanceof AePowerHolder;
            if (allowed) {
                ((AePowerHolder) converter).ae2skills$setAePowerEnabled(packet.enabled());
            }
            // 无论是否被接受都回推权威状态：非主人点击后客户端不会停留在乐观的错值
            boolean actual = converter instanceof AePowerHolder holder && holder.ae2skills$isAePowerEnabled();
            boolean connected = converter instanceof AePowerHolder holder2
                    && holder2.ae2skills$isAeNetworkConnected();
            PacketDistributor.sendToPlayer(player, new AePowerStateS2CPacket(actualPos, actual, connected));
        });
    }
}
