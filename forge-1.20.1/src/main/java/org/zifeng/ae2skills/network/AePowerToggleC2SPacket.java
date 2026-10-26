package org.zifeng.ae2skills.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.zifeng.ae2skills.compat.AePowerFeature;
import org.zifeng.ae2skills.energy.AePowerHolder;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

/**
 * 「使用 AE 能量」开关（客户端 → 服务端）。
 *
 * <p>与本体既有的 ConverterUnlimitedC2SPacket 不同，本开关**只允许机器主人切换**：
 * 无主人的机器一律拒绝（那种机器本来就产不出技能点，拒绝不损失功能，还避免了白抽电网）。
 *
 * <p>1.20.1：改用 {@code SimpleChannel} 的静态 {@code encode/decode/handle}；
 * 发包走 {@link NetworkHandler}（{@code PacketDistributor.sendToPlayer} 在 1.20.1 不存在）。
 */
public record AePowerToggleC2SPacket(BlockPos pos, boolean enabled) {

    public static void encode(AePowerToggleC2SPacket value, FriendlyByteBuf buf) {
        buf.writeBlockPos(value.pos);
        buf.writeBoolean(value.enabled);
    }

    public static AePowerToggleC2SPacket decode(FriendlyByteBuf buf) {
        return new AePowerToggleC2SPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(AePowerToggleC2SPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> apply(packet, ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }

    /**
     * 1.20.1：{@code IPayloadContext} 不存在，服务端玩家改由
     * {@code NetworkEvent.Context#getSender()} 取得（网络上永远是 {@code ServerPlayer}）。
     * 方向校验随之取消——只有 C2S 会注册处理器，服务端本来就收不到反向数据。
     */
    private static void apply(AePowerToggleC2SPacket packet, ServerPlayer player) {
        if (player == null) return;
        SkillPointConverterBlockEntity converter = null;
        if (player.containerMenu instanceof SkillPointConverterMenu menu) {
            converter = menu.getBlockEntity();
        }
        if (converter == null) {
            // 仍回推权威状态，避免客户端停在乐观值
            NetworkHandler.sendAePowerState(player, packet.pos(), false, false);
            return;
        }
        BlockPos actualPos = converter.getBlockPos();
        if (!actualPos.equals(packet.pos())) {
            boolean actual = converter instanceof AePowerHolder holder && holder.ae2skills$isAePowerEnabled();
            boolean connected = converter instanceof AePowerHolder holder2
                    && holder2.ae2skills$isAeNetworkConnected();
            NetworkHandler.sendAePowerState(player, actualPos, actual, connected);
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
        NetworkHandler.sendAePowerState(player, actualPos, actual, connected);
    }
}
