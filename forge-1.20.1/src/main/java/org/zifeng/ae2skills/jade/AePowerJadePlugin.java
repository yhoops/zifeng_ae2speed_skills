package org.zifeng.ae2skills.jade;

import org.zifeng.skilltree.block.SkillPointConverterBlock;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * 附属自己的 Jade 插件：不改本体的两个 provider（它们是 enum 单例），
 * 只在转换机上追加一条故障提示。
 *
 * <p>1.20.1：Jade 的两个注册入口要求的 Class 类型<b>不同</b>（已按 11.13.3+forge 的 javap 实测）：
 * <ul>
 *   <li>{@code IWailaCommonRegistration#registerBlockDataProvider(IServerDataProvider<BlockAccessor>,
 *       Class<? extends BlockEntity>)} —— 服务端数据按<b>方块实体</b>注册；</li>
 *   <li>{@code IWailaClientRegistration#registerBlockComponent(IBlockComponentProvider,
 *       Class<? extends Block>)} —— 客户端组件按<b>方块</b>注册。</li>
 * </ul>
 * 1.21.1 的 Jade 两者都吃 {@code Block}，直接照搬会在 1.20.1 编译失败。
 */
public final class AePowerJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // 1.20.1：服务端数据提供者按 BlockEntity 类型注册。
        registration.registerBlockDataProvider(AePowerJadeDataProvider.INSTANCE,
                SkillPointConverterBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 客户端组件按 Block 类型注册（与 1.21.1 一致）。
        registration.registerBlockComponent(AePowerJadeProvider.INSTANCE, SkillPointConverterBlock.class);
    }
}
