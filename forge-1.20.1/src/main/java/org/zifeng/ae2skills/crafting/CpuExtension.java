package org.zifeng.ae2skills.crafting;

/** Implemented only by the optional AE2 cluster mixin. */
public interface CpuExtension {
    VirtualCpuState zifeng$state();
    long zifeng$baseStorage();
    int zifeng$baseCoProcessors();
    boolean zifeng$readingBase();
}
