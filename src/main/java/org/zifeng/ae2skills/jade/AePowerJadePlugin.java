package org.zifeng.ae2skills.jade;

import org.zifeng.skilltree.block.SkillPointConverterBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * 附属自己的 Jade 插件：不改本体的两个 provider（它们是 enum 单例），
 * 只在转换机上追加一条故障提示。
 */
public final class AePowerJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AePowerJadeDataProvider.INSTANCE, SkillPointConverterBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AePowerJadeProvider.INSTANCE, SkillPointConverterBlock.class);
    }
}
