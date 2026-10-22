package org.zifeng.ae2skills.crafting;

/** Remaining batches; no persistence format or implementation class leaks into the executor. */
public interface TaskAccess {
    long zifeng$getRemaining();
    void zifeng$setRemaining(long value);
}
