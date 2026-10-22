package org.zifeng.ae2skills.mixin.compat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.zifeng.ae2skills.Ae2Effects;

/**
 * Applies the skill storage multiplier before AdvancedAE subtracts active CPU
 * reservations. The native active-CPU map, lifecycle and NBT remain untouched.
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster", remap = false)
public abstract class AdvancedAeCpuSkillsMixin {
    @Shadow @Final private HashMap<UUID, ?> activeCpus;
    @Shadow private long storage;
    @Shadow private long storageMultiplier;
    @Shadow private long remainingStorage;

    @Inject(method = "recalculateRemainingStorage", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$recalculateSkillPool(CallbackInfo ci) {
        zifeng$recalculate();
        ci.cancel();
    }

    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true, require = 1)
    private void zifeng$liveStorage(CallbackInfoReturnable<Long> cir) {
        zifeng$recalculate();
        cir.setReturnValue(remainingStorage);
    }

    @Inject(method = "getRemainingCapacityCPU", at = @At("HEAD"), require = 1)
    private void zifeng$liveRemainingCapacity(CallbackInfoReturnable<Object> cir) {
        zifeng$recalculate();
    }

    @Inject(method = "getCoProcessors", at = @At("RETURN"), cancellable = true, require = 1)
    private void zifeng$scaleCoProcessors(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Ae2Effects.scaledCpuCoProcessors(cir.getReturnValue()));
    }

    private void zifeng$recalculate() {
        long total = storage;
        if (storageMultiplier > 0) total = zifeng$saturatedMultiply(total, storageMultiplier);
        total = Ae2Effects.scaledCpuStorage(Math.max(0L, total));
        long reserved = 0L;
        for (Object cpu : activeCpus.values()) {
            long bytes = zifeng$available(cpu);
            if (bytes > 0) reserved = zifeng$saturatedAdd(reserved, bytes);
        }
        remainingStorage = total <= reserved ? 0L : total - reserved;
    }

    private static long zifeng$available(Object cpu) {
        try {
            Method method = cpu.getClass().getMethod("getAvailableStorage");
            return Math.max(0L, ((Number) method.invoke(cpu)).longValue());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("AdvancedAE CPU storage contract changed", failure);
        }
    }

    private static long zifeng$saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long zifeng$saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
