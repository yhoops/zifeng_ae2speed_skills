package org.zifeng.ae2skills.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.zifeng.ae2skills.energy.AePowerHolder;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** 只上报诊断所需的两个字段，不新增 Jade 常驻行。 */
public enum AePowerJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final String KEY_ENABLED = "ZifengAePower";
    static final String KEY_CONNECTED = "ZifengAeConnected";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getTarget() instanceof AePowerHolder holder) {
            boolean enabled = holder.ae2skills$isAePowerEnabled();
            data.putBoolean(KEY_ENABLED, enabled);
            // 只在启用时才上报连接状态，避免为未使用该功能的机器增加数据
            if (enabled) data.putBoolean(KEY_CONNECTED, holder.ae2skills$isAeNetworkConnected());
        }
    }

    @Override
    public ResourceLocation getUid() {
        // 1.20.1：没有 ResourceLocation.fromNamespaceAndPath，改用双参构造。
        return new ResourceLocation("zifeng_ae2_skills", "ae_power_data");
    }
}
