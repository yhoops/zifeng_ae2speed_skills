package org.zifeng.ae2skills;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.zifeng.ae2skills.compat.AePowerMixinPlugin;
import org.zifeng.ae2skills.compat.Compatibility;
import org.zifeng.ae2skills.network.AePowerStateS2CPacket;
import org.zifeng.ae2skills.network.AePowerToggleC2SPacket;

@Mod(Addon.MOD_ID)
public final class Addon {
    public static final String MOD_ID = "zifeng_ae2_skills";
    public Addon(IEventBus bus) {
        if (!Compatibility.enabled()) {
            // Registries belong to the host/AE2. Let their registration phase finish before
            // reporting a loading error, otherwise FML adds unrelated incomplete-registry errors.
            bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> Compatibility.report());
            return;
        }
        bus.addListener(StatePayload::register);
        // AE 供电：契约不匹配时自行禁用，不影响上面四个技能
        if (AePowerMixinPlugin.converterContractOk()) {
            bus.addListener(AePowerToggleC2SPacket::register);
            bus.addListener(AePowerStateS2CPacket::register);
        }
    }
}
