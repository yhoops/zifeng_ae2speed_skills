package org.zifeng.ae2skills.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.zifeng.ae2skills.crafting.AssemblerExecution;

/** Matrix crafter nodes execute recipes even though the matrix controller owns the provider service. */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter", remap = false)
public abstract class ExtendedMatrixCrafterMixin implements AssemblerExecution {}
