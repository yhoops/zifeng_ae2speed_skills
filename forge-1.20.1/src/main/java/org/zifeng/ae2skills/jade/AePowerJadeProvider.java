package org.zifeng.ae2skills.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 只在**故障时**出声：AE 供电已启用但相邻没有 ME 网络时提示一句。
 * 正常工作中不占用 Jade 行（见「接入诊断」）。
 */
public enum AePowerJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (data == null || !data.contains(AePowerJadeDataProvider.KEY_ENABLED)) return;
        if (!data.getBoolean(AePowerJadeDataProvider.KEY_ENABLED)) return;
        if (data.contains(AePowerJadeDataProvider.KEY_CONNECTED)
                && data.getBoolean(AePowerJadeDataProvider.KEY_CONNECTED)) {
            return;
        }
        tooltip.add(Component.translatable("ui.zifeng_ae2_skills.ae_not_connected"));
    }

    @Override
    public ResourceLocation getUid() {
        // 1.20.1：没有 ResourceLocation.fromNamespaceAndPath，改用双参构造。
        return new ResourceLocation("zifeng_ae2_skills", "ae_power_hint");
    }
}
