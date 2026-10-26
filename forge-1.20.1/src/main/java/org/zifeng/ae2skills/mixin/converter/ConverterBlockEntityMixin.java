package org.zifeng.ae2skills.mixin.converter;

import appeng.api.networking.energy.IEnergyService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import org.zifeng.ae2skills.network.NetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.energy.AePowerHolder;
import org.zifeng.ae2skills.energy.AePowerSource;
import org.zifeng.ae2skills.energy.GatedEnergyStorage;
import org.zifeng.ae2skills.network.AePowerStateS2CPacket;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import org.zifeng.skilltree.menu.SkillPointConverterMenu;

/**
 * 让本体的技能点转换机支持「使用 AE 能量」：
 * <ul>
 *   <li>相邻 6 面发现 ME 网络并抽取 AE，按 AE2 自身汇率折算成 FE 当量喂进本体原有的公开入口
 *       {@code receiveEnergyLong}，因此速率限制、缓冲上限、进度累积全部复用本体实现。</li>
 *   <li>开启时关闭 FE 入口（供电方式互斥）。</li>
 *   <li>只要相邻网络仍然连接就保留进度，不走本体的「20 tick 无输入即清零」。</li>
 * </ul>
 * 开关默认为关闭，且与四个 AE 技能解耦：本体契约不匹配时本 mixin 整体不应用。
 */
@Mixin(value = SkillPointConverterBlockEntity.class, remap = false)
public abstract class ConverterBlockEntityMixin implements AePowerHolder {
    /** 独立 NBT 键，避开本体的扁平 PascalCase 键（Progress / UnlimitedInput / Owner ...）。 */
    private static final String ZIFENG$NBT_KEY = "Ae2UseAeEnergy";
    /** 单次请求上限，避免无限制模式下把 long 撑到溢出。 */
    @Unique private static final long ZIFENG$MAX_REQUEST = Long.MAX_VALUE / 4;

    @Shadow private long progress;
    @Shadow private long lastReceiveTick;
    @Shadow public abstract long getInputRate();
    @Shadow public abstract boolean isUnlimitedInput();
    @Shadow public abstract long getCapacityLong();
    @Shadow public abstract long receiveEnergyLong(long maxReceive, boolean simulate);
    @Shadow protected abstract long getThreshold();
    @Shadow public abstract IEnergyStorage getEnergyStorage();

    @Unique private boolean ae2skills$aePower;
    @Unique private IEnergyStorage ae2skills$gatedStorage;
    @Unique private boolean ae2skills$lastConnected;
    /** 本路径正在注入能量：FE 入口闸必须放行自己人（否则 AE 供电永远无法生效）。 */
    @Unique private boolean ae2skills$injectingAe;
    /**
     * AE 是小数、本体账本是整数 FE，余量必须累积而不是每 tick 截断。
     * 否则网络只给出不到 1 FE 当量时，能量被抽走却记不上任何进度（机器变成只进不出的漏勺）。
     */
    @Unique private double ae2skills$feCarry;

    @Override
    public boolean ae2skills$isAePowerEnabled() {
        return ae2skills$aePower;
    }

    @Override
    public void ae2skills$setAePowerEnabled(boolean value) {
        if (ae2skills$aePower == value) return;
        ae2skills$aePower = value;
        ae2skills$feCarry = 0;
        ((BlockEntity) (Object) this).setChanged();
    }

    @Override
    public boolean ae2skills$isAeNetworkConnected() {
        return AePowerSource.isConnected(((BlockEntity) (Object) this).getLevel(),
                ((BlockEntity) (Object) this).getBlockPos());
    }

    /** AE 取电。放在本体 tick 的最前面；先自行排除红石关闭，以免白耗电网。 */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void ae2skills$drawAePower(Level level, BlockPos pos, BlockState state,
            SkillPointConverterBlockEntity be, CallbackInfo ci) {
        ConverterBlockEntityMixin self = (ConverterBlockEntityMixin) (Object) be;
        if (!self.ae2skills$aePower) return;
        if (level == null || level.isClientSide) return;
        if (be.isRedstoneBlocked()) return;

        IEnergyService energy = AePowerSource.find(level, pos);
        self.ae2skills$publishConnection(level, pos, energy != null);
        // 未连接 = 电源消失，交给本体的断供清零逻辑（与 FE 被拔线一致）
        if (energy == null) return;

        // 连接保持：网络仍在，即使这一 tick 抽不到电也不清零进度
        self.lastReceiveTick = level.getGameTime();

        long threshold = self.getThreshold();
        if (threshold <= 0) return;
        // 渐进抽取：默认每 tick 至多凑满「当前一个技能点」；
        // 按下「无限制输入」则解除该上限，以机器缓冲余量为限（进而只受网络可供功率约束）。
        long room = self.isUnlimitedInput()
                ? Math.max(1, self.getCapacityLong() - self.progress)
                : Math.min(self.getInputRate(), Math.max(0, threshold - self.progress));
        if (room <= 0) return;

        // 整段都在「自己人」标记内：本体的模拟查询同样会被 FE 入口闸拦住。
        self.ae2skills$injectingAe = true;
        try {
            // 先问本体这一 tick 究竟能吃下多少（不改变状态），避免抽出网络能量却无处记账
            long accepted = self.receiveEnergyLong(Math.min(room, ZIFENG$MAX_REQUEST), true);
            if (accepted <= 0) return;

            double drawn = AePowerSource.draw(energy, AePowerSource.aeForFe(accepted));
            if (drawn <= 0) return;

            self.ae2skills$feCarry += AePowerSource.feForAe(drawn);
            long fe = (long) self.ae2skills$feCarry;
            // 还没攒够 1 FE：留着余量，下一 tick 继续累积
            if (fe <= 0) return;
            self.ae2skills$feCarry -= fe;

            self.receiveEnergyLong(fe, false);
        } finally {
            self.ae2skills$injectingAe = false;
        }
    }

    /** 相邻网络连接状态变化时，只通知正打开本机器界面的玩家。 */
    @Unique
    private void ae2skills$publishConnection(Level level, BlockPos pos, boolean connected) {
        if (connected == ae2skills$lastConnected) return;
        ae2skills$lastConnected = connected;
        if (!(level instanceof ServerLevel serverLevel)) return;
        var players = serverLevel.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (player.serverLevel() == serverLevel
                    && player.containerMenu instanceof SkillPointConverterMenu menu
                    && pos.equals(menu.getBlockPos())) {
                NetworkHandler.sendAePowerState(player, pos, ae2skills$aePower, connected);
            }
        }
    }

    /** FE 入口闸：AE 供电开启时对外的 FE 接收面整体拒绝。 */
    @Inject(method = "getEnergyStorage", at = @At("RETURN"), cancellable = true)
    private void ae2skills$gateFe(CallbackInfoReturnable<IEnergyStorage> cir) {
        IEnergyStorage original = cir.getReturnValue();
        if (original == null) return;
        if (ae2skills$gatedStorage == null) ae2skills$gatedStorage = new GatedEnergyStorage(original, this);
        cir.setReturnValue(ae2skills$gatedStorage);
    }

    /** Flux 的 long 通道同样受互斥约束；但本路径自己的取电与注入必须放行。 */
    @Inject(method = "receiveEnergyLong", at = @At("HEAD"), cancellable = true)
    private void ae2skills$gateLongFe(long maxReceive, boolean simulate, CallbackInfoReturnable<Long> cir) {
        if (ae2skills$aePower && !ae2skills$injectingAe) cir.setReturnValue(0L);
    }

    // 1.20.1：存盘方法是 SRG 名 m_183515_(CompoundTag)V，**没有** HolderLookup.Provider 参数
    // （那是 1.20.5+ 才有）。描述符对不上会让整个 mixin 不应用，「使用 AE 能量」彻底失效。
    @Inject(method = "m_183515_", at = @At("TAIL"))
    private void ae2skills$saveAe(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(ZIFENG$NBT_KEY, ae2skills$aePower);
    }

    @Inject(method = "m_142466_", at = @At("TAIL"))
    private void ae2skills$loadAe(CompoundTag tag, CallbackInfo ci) {
        // 老存档无此键 => false => 保持 FE 供电，行为零变化
        ae2skills$aePower = tag.getBoolean(ZIFENG$NBT_KEY);
    }

    /** 打开界面时推送一次状态，避免 GUI 读到过期值。 */
    @Inject(method = "m_7208_", at = @At("TAIL"))
    private void ae2skills$syncOnOpen(int containerId, Inventory inventory, Player player,
            CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
        NetworkHandler.sendAePowerState(serverPlayer, pos, ae2skills$aePower,
                AePowerSource.isConnected(((BlockEntity) (Object) this).getLevel(), pos));
    }
}