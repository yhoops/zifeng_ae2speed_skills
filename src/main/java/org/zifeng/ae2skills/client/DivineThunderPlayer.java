package org.zifeng.ae2skills.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 真正的雷声播放；只有 {@link ClientEvents}（{@code Dist.CLIENT} 专属）会调用它。
 *
 * <h2>为什么是两段而不是一段</h2>
 * 原版 {@code LightningBolt#tick} 在 {@code life == 2} 时同时播放两个声音：
 * 低频轰鸣 {@link SoundEvents#LIGHTNING_BOLT_THUNDER}（音量 10000、音高 0.8）与
 * 爆裂声 {@link SoundEvents#LIGHTNING_BOLT_IMPACT}（音量 2.0、音高 0.5）。
 * 这里逐字照抄这两个数值，因此听到的就是真正的雷电。
 *
 * <h2>为什么不用服务端 {@code Level#playSound}</h2>
 * 那条路径在客户端按 {@code except == minecraft.player} 过滤，语义是「给除自己外的听众」，
 * 与「只放给达成者」正好相反；{@code playLocalSound} 才是无衰减、只此一人的入口。
 * 这同时解释了为什么必须自己发一个 S2C 包而不是复用 {@code ClientboundSoundPacket}。
 */
public final class DivineThunderPlayer {
    private DivineThunderPlayer() {}

    public static void play() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.level() instanceof ClientLevel level)) return;
        double x = minecraft.player.getX();
        double y = minecraft.player.getY();
        double z = minecraft.player.getZ();
        level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0F, 0.8F, false);
        level.playLocalSound(x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F, 0.5F, false);
    }
}
