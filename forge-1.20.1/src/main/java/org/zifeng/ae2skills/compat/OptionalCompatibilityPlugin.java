package org.zifeng.ae2skills.compat;

import java.util.List;
import java.util.Set;
// 1.20.1：包名由 net.neoforged.fml.loading 改为 net.minecraftforge.fml.loading。
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Missing optional mods are skipped; installed adapters are checked as a complete group. */
public final class OptionalCompatibilityPlugin implements IMixinConfigPlugin {
    public static boolean installedFor(String mixin) {
        String id;
        if (mixin.contains("Advanced")) id = "advanced_ae";
        else if (mixin.endsWith("ExtendedMatrixCrafterMixin")) id = "extendedae";
        else if (mixin.endsWith("UfoCpuPoolSkillsMixin")) id = "ufo";
        else if (mixin.endsWith("ExtendedAssemblerSpeedMixin")) id = "extendedae_plus";
        else return false;
        return LoadingModList.get().getMods().stream().anyMatch(mod -> mod.getModId().equals(id));
    }

    public void onLoad(String pkg) {
        Compatibility.checkOptionalMixins("zifeng_ae2_skills.compat.mixins.json");
    }
    public boolean shouldApplyMixin(String target, String mixin) {
        return Compatibility.enabled() && installedFor(mixin);
    }
    public String getRefMapperConfig() { return null; }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
