package org.zifeng.ae2skills.test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

/**
 * 测试用的 NBT 探针：本体的 saveAdditional / loadAdditional 是 protected，
 * 因此用反射在测试内取用，不为了测试放宽本体成员的可见性。
 */
final class SaveProbe {
    private SaveProbe() {}

    static CompoundTag save(Object blockEntity) {
        try {
            var method = blockEntity.getClass().getDeclaredMethod("saveAdditional",
                    CompoundTag.class, HolderLookup.Provider.class);
            method.setAccessible(true);
            var tag = new CompoundTag();
            method.invoke(blockEntity, tag, registryAccess(blockEntity));
            return tag;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void load(Object blockEntity, CompoundTag tag) {
        try {
            var method = blockEntity.getClass().getDeclaredMethod("loadAdditional",
                    CompoundTag.class, HolderLookup.Provider.class);
            method.setAccessible(true);
            method.invoke(blockEntity, tag, registryAccess(blockEntity));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static HolderLookup.Provider registryAccess(Object blockEntity) {
        try {
            var getLevel = blockEntity.getClass().getMethod("getLevel");
            var level = (net.minecraft.world.level.Level) getLevel.invoke(blockEntity);
            return level.registryAccess();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
    /** 把 long 字段写进方块实体（测试探针；本体字段是 private，生产代码不放宽可见性）。 */
    static void setLong(Object blockEntity, String field, long value) {
        try {
            Class<?> type = blockEntity.getClass();
            java.lang.reflect.Field target = null;
            while (type != null && target == null) {
                try {
                    target = type.getDeclaredField(field);
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            if (target == null) throw new NoSuchFieldException(field);
            target.setAccessible(true);
            target.setLong(blockEntity, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

}
