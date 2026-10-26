package org.zifeng.ae2skills.client;

import appeng.core.localization.Tooltips;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.LoggerFactory;
import org.zifeng.ae2skills.Addon;

/** Display-only protection: never changes CPU capacity, reservations or job admission. */
public final class CpuStorageDisplay {
    // AE2 19.2.17 increments past its last byte unit at 1000 TiB.
    private static final long BYTE_FORMAT_LIMIT = 1_000L * (1L << 40);
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private CpuStorageDisplay() {}

    public static MutableComponent tooltip(long bytes, LongFunction<MutableComponent> formatter) {
        if (bytes == Long.MAX_VALUE) {
            return Component.literal("∞").withStyle(Tooltips.NUMBER_TEXT);
        }
        if (bytes >= BYTE_FORMAT_LIMIT) return rawBytes(bytes);
        try {
            return formatter.apply(bytes);
        } catch (RuntimeException error) {
            if (REPORTED.compareAndSet(false, true)) {
                LoggerFactory.getLogger(Addon.MOD_ID).warn(
                        "AE2 CPU capacity tooltip formatting failed (bytes={}). "
                                + "Using raw bytes for display; CPU capacity and crafting are unchanged. "
                                + "Further failures are suppressed for this client session. "
                                + "Please attach logs/latest.log when reporting this issue.", bytes, error);
            }
            return rawBytes(bytes).append(Component.translatable(
                    "ui.zifeng_ae2_skills.cpu_storage_format_fallback").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static MutableComponent rawBytes(long bytes) {
        return Component.literal(Long.toString(bytes) + " B").withStyle(Tooltips.NUMBER_TEXT);
    }
}
