package org.zifeng.ae2skills;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.zifeng.ae2skills.compat.Compatibility;
import org.zifeng.ae2skills.network.NetworkHandler;

/**
 * 1.20.1：{@code @Mod} 来自 {@code net.minecraftforge.fml.common.Mod}；
 * 构造注入的 {@code IEventBus} 在 1.20.1 Forge 里不存在，改为从
 * {@link FMLJavaModLoadingContext#get()} 取模组事件总线。
 */
@Mod(Addon.MOD_ID)
public final class Addon {
    public static final String MOD_ID = "zifeng_ae2_skills";

    public Addon() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // 能力检测必须无条件执行：它是「混入半应用」的唯一发现手段，而半应用与预检失败
        // 是两条互不相关的原因（预检看的是类结构是否齐全，混入能否应用还取决于别家模组）。
        // 放在 FMLCommonSetupEvent 是因为那时目标类都已经加载完毕。
        bus.addListener((FMLCommonSetupEvent event) -> Compatibility.checkApplied());
        if (!Compatibility.enabled()) {
            // Registries belong to the host/AE2. Let their registration phase finish before
            // reporting a loading error, otherwise FML adds unrelated incomplete-registry errors.
            bus.addListener((FMLCommonSetupEvent event) -> Compatibility.report());
            return;
        }
        // 1.20.1 没有 RegisterPayloadHandlersEvent，四个消息统一在这里注册。
        //
        // 1.21.1 源码里 AE 供电的两个包是按 AePowerMixinPlugin.converterContractOk() 条件注册的。
        // 1.20.1 这里改为<b>无条件注册</b>：SimpleChannel 的消息序号必须与服务端/客户端两侧
        // 逐一对齐，而契约判定是按 dist 分别执行的（客户端会额外校验界面类），
        // 一旦两侧结论不同就会整条通道握手失败、连四个技能一起失联。
        // 功能闸门本来就在下面两处，注册本身无副作用：
        //   · mixin 不应用 → 机器上根本没有 AePowerHolder；
        //   · AePowerToggleC2SPacket.handle 内先查 AePowerFeature.enabled()。
        NetworkHandler.register();
    }
}
