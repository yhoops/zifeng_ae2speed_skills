package org.zifeng.ae2skills.client;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.ae2skills.network.AePowerStateS2CPacket;

/**
 * 客户端持有的 AE 供电状态快照。故意不引用任何客户端专用类型，
 * 因此可以作为数据包处理器在专用服务器上被安全地链接。
 */
public final class ClientAePower {
    private static BlockPos pos;
    private static boolean enabled;
    private static boolean connected;
    private static int revision;
    private ClientAePower() {}

    public static void accept(AePowerStateS2CPacket data, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            pos = data.pos();
            enabled = data.enabled();
            connected = data.connected();
            revision++;
        });
    }

    /** 丢弃断开连接后不再可信的本地快照。 */
    public static void reset() {
        pos = null;
        enabled = false;
        connected = false;
        revision++;
    }

    /** 该位置的状态是否已知（未知时 GUI 回退到「关闭」，与默认值一致）。 */
    public static boolean known(BlockPos at) {
        return pos != null && pos.equals(at);
    }

    public static boolean enabled(BlockPos at) {
        return known(at) && enabled;
    }

    public static boolean connected(BlockPos at) {
        return known(at) && connected;
    }

    public static int revision() {
        return revision;
    }
}
