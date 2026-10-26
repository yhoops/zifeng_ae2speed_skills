package org.zifeng.ae2skills.client;

/**
 * 雷声请求的客户端状态位。
 *
 * <p>本类<b>故意不引用任何客户端专用类型</b>（与 {@link ClientAePower} 同一口径），
 * 因此可以作为数据包处理器在专用服务器上被安全地链接；真正的播放由
 * {@link ClientEvents} 里 {@code Dist.CLIENT} 专属的 tick 处理完成。
 * 这里只是一个「待播放」标记，客户端 tick 会立刻消费它（最多 1 tick 延迟）。
 *
 * <p>1.20.1：{@code IPayloadContext} 不存在，处理器改为无参。
 */
public final class ClientDivineThunder {
    private static boolean pending;
    private ClientDivineThunder() {}

    public static void accept() {
        pending = true;
    }

    /** 取走并清空待播放标记；返回 {@code true} 表示这一 tick 应当播放雷声。 */
    public static boolean consumePending() {
        boolean was = pending;
        pending = false;
        return was;
    }

    /** 断开连接时丢弃未播放的请求，避免下次进服突然响一声。 */
    public static void reset() {
        pending = false;
    }
}
