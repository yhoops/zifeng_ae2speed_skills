package org.zifeng.ae2skills.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** One preflight decision for the entire addon, made before any of its mixins is applied. */
public final class CompatibilityPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) {
        org.spongepowered.asm.mixin.Mixins.registerErrorHandlerClass("org.zifeng.ae2skills.compat.MixinFailureHandler");
        Compatibility.check();
    }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) { return Compatibility.enabled(); }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
