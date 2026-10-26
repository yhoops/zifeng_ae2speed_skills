package org.zifeng.ae2skills.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.LoggerFactory;
import org.zifeng.ae2skills.Addon;
import org.zifeng.ae2skills.StatePayload;

/**
 * 1.20.1 网络层集中注册。
 *
 * <h2>为什么整层重写</h2>
 * 1.21.1 的 {@code CustomPacketPayload} + {@code StreamCodec} + {@code RegisterPayloadHandlersEvent}
 * 在 1.20.1 Forge 里全部不存在；这里改用 1.20.1 的 {@code SimpleChannel}，
 * 由本类统一登记四个消息的方向、序号与编解码器。
 *<p>
 * 实测说明（javap forge 1.20.1-47.4.28）：该版本<b>没有</b>
 * {@code net.minecraftforge.network.simple.IMessage} 这个接口（那是 1.13~1.16 时代的东西），
 * 因此消息类不实现任何接口，只提供静态 {@code encode}/{@code decode}/{@code handle}，
 * 由 {@link SimpleChannel#registerMessage} 以函数式方式绑定。
 */
public final class NetworkHandler {
    private static final String PROTOCOL = "1";

    /** 本附属唯一的通道；1.20.1 惯例是「一个 mod 一个 channel + 多个 discriminated 消息」。 */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Addon.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    /** 消息序号必须稳定且唯一；只在本类内递增，避免各处手写 id 撞车。 */
    private static int nextId;
    private static boolean registered;

    private NetworkHandler() {}

    /** 由 {@link Addon} 在构造期调用一次。重复调用是安全的。 */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(nextId++, StatePayload.class,
                StatePayload::encode, StatePayload::decode, StatePayload::handle);
        CHANNEL.registerMessage(nextId++, DivineThunderS2CPacket.class,
                DivineThunderS2CPacket::encode, DivineThunderS2CPacket::decode, DivineThunderS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, AePowerStateS2CPacket.class,
                AePowerStateS2CPacket::encode, AePowerStateS2CPacket::decode, AePowerStateS2CPacket::handle);
        CHANNEL.registerMessage(nextId++, AePowerToggleC2SPacket.class,
                AePowerToggleC2SPacket::encode, AePowerToggleC2SPacket::decode, AePowerToggleC2SPacket::handle);
        LoggerFactory.getLogger(Addon.MOD_ID).debug("[网络] 已注册 {} 个消息类型", nextId);
    }

    // ---------------------------------------------------------------- 发送入口
    // 下面这些静态方法是给其它包（含 mixin）用的稳定门面；调用方不需要知道 SimpleChannel 的细节。

    /** 发给单个玩家；按消息类型分派，未知类型只记一条警告而不抛异常。 */
    public static void sendToPlayer(ServerPlayer player, Object msg) {
        if (player == null || msg == null) return;
        if (!(msg instanceof StatePayload || msg instanceof DivineThunderS2CPacket
                || msg instanceof AePowerStateS2CPacket)) {
            LoggerFactory.getLogger(Addon.MOD_ID).warn("[网络] 无法发送未知消息类型：{}", msg.getClass().getName());
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** 发给所有在线玩家。 */
    public static void sendToAllPlayers(Object msg) {
        if (msg == null) return;
        if (!(msg instanceof StatePayload || msg instanceof DivineThunderS2CPacket
                || msg instanceof AePowerStateS2CPacket)) {
            LoggerFactory.getLogger(Addon.MOD_ID).warn("[网络] 无法广播未知消息类型：{}", msg.getClass().getName());
            return;
        }
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    /** 客户端 → 服务端（GUI 点击、开关切换等）。 */
    public static void sendToServer(Object msg) {
        if (msg == null) return;
        if (!(msg instanceof AePowerToggleC2SPacket)) {
            LoggerFactory.getLogger(Addon.MOD_ID).warn("[网络] 无法上传未知消息类型：{}", msg.getClass().getName());
            return;
        }
        CHANNEL.sendToServer(msg);
    }

    /** 满级成就的雷声。位置与音色由客户端本地决定，服务端载荷为空，因此另外两个参数只保留签名兼容。 */
    public static void sendDivineThunder(ServerPlayer player, net.minecraft.core.BlockPos pos, boolean flag) {
        sendToPlayer(player, DivineThunderS2CPacket.INSTANCE);
    }

    /** 回推转换机的权威 AE 供电状态。 */
    public static void sendAePowerState(ServerPlayer player, net.minecraft.core.BlockPos pos,
                                        boolean enabled, boolean connected) {
        sendToPlayer(player, new AePowerStateS2CPacket(pos, enabled, connected));
    }

    /**
     * 按对外约定的两参签名保留。
     *
     * <p>{@link AePowerToggleC2SPacket} 是<b>客户端 → 服务端</b>的消息，服务端无法「发给某个玩家」
     * （方向由通道校验，客户端也不会注册它的处理器）。真正需要的是
     * {@link #sendToServer(Object)} / {@link #sendAePowerToggle(net.minecraft.core.BlockPos, boolean)}。
     * 这里做成显式的空实现并留日志，避免调用方以为它生效，也避免抛异常打断 tick。
     */
    public static void sendAePowerToggle(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        LoggerFactory.getLogger(Addon.MOD_ID).debug(
                "[网络] sendAePowerToggle(ServerPlayer, BlockPos) 是 C2S 消息，服务端无对应语义，已忽略：{}", pos);
    }

    /** C2S 真实入口：客户端把「使用 AE 能量」开关意图发给服务端。 */
    public static void sendAePowerToggle(net.minecraft.core.BlockPos pos, boolean enabled) {
        sendToServer(new AePowerToggleC2SPacket(pos, enabled));
    }

    /**
     * 同 {@link #sendAePowerToggle(net.minecraft.core.BlockPos, boolean)}，
     * 只是把「方向」写进方法名——客户端界面代码用这个名字，读起来不会误以为是 S2C。
     */
    public static void sendAePowerToggleToServer(net.minecraft.core.BlockPos pos, boolean enabled) {
        sendAePowerToggle(pos, enabled);
    }
}
