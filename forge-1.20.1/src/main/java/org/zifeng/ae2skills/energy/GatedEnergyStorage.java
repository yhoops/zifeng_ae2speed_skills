package org.zifeng.ae2skills.energy;

// 1.20.1：Forge 的能量接口在 net.minecraftforge.energy，方法签名与 NeoForge 同名同参
import net.minecraftforge.energy.IEnergyStorage;

/**
 * FE 入口闸：AE 供电开启时，本体的 FE 接收面整体关闭（供电方式互斥）。
 * 未开启时逐字转发给本体原有的存储实现，行为零变化。
 */
public final class GatedEnergyStorage implements IEnergyStorage {
    private final IEnergyStorage delegate;
    private final AePowerHolder holder;

    public GatedEnergyStorage(IEnergyStorage delegate, AePowerHolder holder) {
        this.delegate = delegate;
        this.holder = holder;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return holder.ae2skills$isAePowerEnabled() ? 0 : delegate.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return delegate.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return delegate.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return delegate.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return delegate.canExtract();
    }

    @Override
    public boolean canReceive() {
        return !holder.ae2skills$isAePowerEnabled() && delegate.canReceive();
    }
}
