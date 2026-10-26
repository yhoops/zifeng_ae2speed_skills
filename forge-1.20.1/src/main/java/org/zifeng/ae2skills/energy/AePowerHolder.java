package org.zifeng.ae2skills.energy;

/**
 * 由 mixin 让本体的技能点转换机实现本接口，从而在不新增方块、不依赖 ContainerData 的前提下
 * 承载「使用 AE 能量」开关与其 NBT 持久化。
 */
public interface AePowerHolder {
    boolean ae2skills$isAePowerEnabled();

    void ae2skills$setAePowerEnabled(boolean value);

    /** 相邻 ME 网络当前是否可抽取（用于 Jade 的未连接提示）。 */
    boolean ae2skills$isAeNetworkConnected();
}
