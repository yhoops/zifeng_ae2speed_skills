package org.zifeng.ae2skills.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.zifeng.ae2skills.crafting.AssemblerExecution;

/** Super matrix controllers use the shared AE2 scheduler hook, never a second progress multiplier. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity", remap = false)
public abstract class ExtendedAssemblerSpeedMixin implements AssemblerExecution {}
