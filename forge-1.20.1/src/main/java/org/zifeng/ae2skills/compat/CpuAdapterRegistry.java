package org.zifeng.ae2skills.compat;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.crafting.VirtualCpus;

/**
 * 第三方合成 CPU 的<b>通用</b>适配器：不写死任何模组类名、不扫描包名，
 * 只依赖 AE2 官方接口与本附属已发布的技能数值。
 *
 * <h2>为什么需要它</h2>
 * AE2 的 {@code CraftingService} 只用 {@code Set<CraftingCPUCluster>} 保存 CPU，并在
 * {@code getCpus()} / {@code findSuitableCraftingCPU()} 内对元素做 {@code checkcast
 * CraftingCPUCluster}。因此：
 * <ul>
 *   <li><b>形态 a</b>——第三方直接用真实的 {@link CraftingCPUCluster}（含子类、或经 mixin 伪造继承）。
 *       本附属原有的 {@code CraftingCpuSpeedMixin} 已能命中，无需本类介入。</li>
 *   <li><b>形态 b</b>——第三方自有 {@link ICraftingCPU} 实现（例如只出现在自己的 CPU 池里、由自身
 *       网络服务向玩家暴露）。它不进 {@code craftingCPUClusters}，原生 mixin 完全命中不到，
 *       这正是「装了别的模组就不生效」的根因。</li>
 * </ul>
 *
 * <h2>保守模式：宁可不管，绝不破坏（1.1.5 起）</h2>
 * 1.1.4 曾把 {@code getCpus()} 里的每个非 {@link CraftingCPUCluster} 元素包成
 * {@link ScaledCpu}，结果与 Neo Eco AE Extension（mod id {@code neoecoae}）严重冲突。教训是
 * <b>{@code getCpus()} 的返回值是身份敏感的</b>——别的模组会把它当作「引用令牌」持有：
 * <ul>
 *   <li>ECO 的 {@code CraftingService.onGetCpus} 把自己的 {@code ECOCraftingCPU}（含一个稳定的
 *       {@code getFakeCPU()} 占位 CPU）塞进 builder，其 {@code hasCpu} 注入用
 *       <b>{@code activeCpu == cpu} / {@code getFakeCPU() == cpu} 严格引用相等</b>来判断
 *       「这个 CPU 是不是我的」。</li>
 *   <li>本类每次 {@code getCpus()} 都 new 一个新装饰器，对 ECO 的 {@code hasCpu} 永远是陌生引用
 *       → 选中态失效：CPU 列表点击无反应、合成状态不显示（但服务端任务其实已提交并执行）。</li>
 *   <li>叠加容量被改成 {@code Long.MAX_VALUE} 后，AE2 侧看到的「能否承接」与 ECO 侧按逻辑实例
 *       的记账脱节，进一步放大错乱。</li>
 * </ul>
 * 因此本类改为<b>只要发现「外来 CPU」就整体退出</b>：{@link #wrapAll} 一旦在集合里看到既不是
 * {@link CraftingCPUCluster}、也不是本类装饰器的元素，就认定「有别的模组在管理 CPU 身份」，
 * 直接原样返回整个集合，不包装任何对象。
 * <p>代价是诚实的：形态 b 模组（含 ECO）不再从算力超频／分裂算力获得任何放大，行为回到 1.1.3
 * 那样「只对原生与形态 a 生效」。这是有意取舍——对形态 a 的放大走原生
 * {@code CraftingCpuSpeedMixin}，不产生陌生引用，始终安全。
 * <p><b>绝不让包装对象流入 {@code submitJob}</b>：AE2 会对提交路径上的 CPU 做
 * {@code checkcast CraftingCPUCluster}，换成装饰器会直接 {@code ClassCastException}。
 * 因此包装只发生在 {@code getCpus()}（只读展示），{@code submitJob} /
 * {@code findSuitableCraftingCPU} 一律不动。
 * <p><b>不为形态 b 创建虚拟 CPU</b>：虚拟 CPU 依赖 {@link CraftingCPUCluster} 的 NBT、集群
 * 生命周期与 {@code craftingLogic} 访问，形态 b 不保证这些。强行创建会在存档与生命周期上
 * 引入不确定性。这是有意的范围收敛，已在 README 与验证记录中写明。
 */
public final class CpuAdapterRegistry {
    private static final Logger LOG = LoggerFactory.getLogger("zifeng_ae2_skills");

    /** 反射 / 第三方实现异常时的日志限流（毫秒），避免 CPU 列表每帧刷屏。 */
    private static final long DIAG_INTERVAL_MS = 5000L;
    private static long lastDiag;
    private static volatile boolean degraded;

    private CpuAdapterRegistry() {}

    /**
     * 是否需要对第三方 CPU 做数值缩放。
     * <p>技能全关（默认）时返回 false —— 此时 {@link #wrap(ICraftingCPU)} 原样返回入参，
     * 行为与未安装本附属逐字节一致（零开销直通）。
     */
    public static boolean active() {
        return Ae2Effects.getCpuSpeedLevel() != 0 || Ae2Effects.getCpuInfiniteLevel() != 0;
    }

    /** 本类是否因第三方实现异常而降级（供诊断与 GameTest 观察）。 */
    public static boolean degraded() { return degraded; }

    /**
     * 判断一个元素是否是「外来 CPU」——即既不是 AE2 原生 {@link CraftingCPUCluster}，
     * 也不是本类自己的装饰器。
     * <p>看到这样的元素，就意味着有别的模组把它自己的 CPU 实现放进了 {@code getCpus()}，
     * 而该模组几乎必然配套接管了 {@code hasCpu} 的引用相等语义。此时本类必须整体退出。
     */
    public static boolean isForeign(ICraftingCPU cpu) {
        return cpu != null && !(cpu instanceof CraftingCPUCluster) && !(cpu instanceof ScaledCpu);
    }

    /**
     * 保守模式的核心：对整个 CPU 集合决定是否包装。
     * <p>只要集合里出现任何一个 {@link #isForeign(ICraftingCPU) 外来 CPU}，就<b>原样返回入参
     * （{@code ==}）</b>，一个都不包装。这是 1.1.5 对 1.1.4 事故的修正：无法在不破坏第三方
     * 身份语义的前提下放大其数值，因此选择完全不碰。
     * <p>技能全关时同样原样返回（零开销直通）。
     *
     * @param cpus     {@code CraftingService.getCpus()} 的原始返回值，可为 null
     * @param rewrapFn 把集合元素逐个包装并重新构建集合的函数（保持不可变与元素顺序）
     * @return 包装后的集合，或入参本身
     */
    public static ImmutableSet<ICraftingCPU> wrapAll(ImmutableSet<ICraftingCPU> cpus,
                                                     java.util.function.UnaryOperator<ImmutableSet<ICraftingCPU>> rewrapFn) {
        if (cpus == null || cpus.isEmpty() || !active()) return cpus;
        for (ICraftingCPU cpu : cpus) {
            if (isForeign(cpu)) {
                // 有模组在管理 CPU 身份语义，本类绝不介入——否则会像 1.1.4 对待 ECO 那样
                // 制造陌生引用，让对方的 hasCpu(==) 判定失败。
                diag("检测到第三方自有 CPU（{}），已退出通用适配以避免破坏其身份语义；"
                        + "该模组的 CPU 不会获得算力超频／分裂算力的放大",
                        cpu.getClass().getName());
                return cpus;
            }
        }
        // 集合里全是原生 CraftingCPUCluster：原生 Mixin 已处理，无需包装（保持身份稳定）。
        return cpus;
    }

    /**
     * 把一个 CPU 包装成「技能放大后」的只读视图。
     * <p><b>注意</b>：本方法已不再用于 {@code getCpus()} 的集合路径（见 {@link #wrapAll}）。
     * 保留它是为了让 GameTest 能直接验证装饰器本身的数值语义。
     * <p>对以下情形<b>原样返回入参</b>（{@code ==}，保证零开销与身份稳定）：
     * <ul>
     *   <li>{@code cpu == null}</li>
     *   <li>技能全关（见 {@link #active()}）</li>
     *   <li>本身就是 {@link CraftingCPUCluster}（原生路径已由 {@code CraftingCpuSpeedMixin} 处理，
     *       再包一层会重复放大）</li>
     *   <li>已经是本类的包装（幂等，避免嵌套放大）</li>
     *   <li>包装过程抛异常（降级返回原对象，绝不把异常抛给 AE2）</li>
     * </ul>
     *
     * @param cpu 任意合成 CPU，可为 null
     * @return 包装后的只读视图，或入参本身
     * @deprecated 集合路径请改用 {@link #wrapAll}；本方法会制造陌生引用，对身份敏感的第三方
     *         （如 ECO 的 {@code hasCpu}）不安全。
     */
    @Deprecated
    public static ICraftingCPU wrap(ICraftingCPU cpu) {
        if (cpu == null) return null;
        // 原生 CPU（含子类）走 CraftingCpuSpeedMixin，避免双重放大与身份变化。
        if (cpu instanceof CraftingCPUCluster) return cpu;
        if (cpu instanceof ScaledCpu) return cpu;
        if (!active()) return cpu;
        try {
            return new ScaledCpu(cpu);
        } catch (Throwable failure) {
            // 第三方实现异常（例如构造期取不到值）不得影响 AE2 的 CPU 列表。
            degraded = true;
            diag("第三方 CPU 适配失败，已按原值展示：{}", cpu.getClass().getName(), failure);
            return cpu;
        }
    }

    private static void diag(String message, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < DIAG_INTERVAL_MS) return;
        lastDiag = now;
        LOG.warn("[AE2 技能] " + message, args);
    }

    /**
     * 只读装饰器：缩放并行处理单元与合成存储容量，<b>其余方法一律委托</b>给被包装的 CPU。
     * <p>无状态、不缓存——同一 inner 每次 {@code getCpus()} 得到新包装，避免与 AE2 的
     * {@code hasCpu(ICraftingCPU)} 身份比较、{@code GridCraftingCpuChange} 事件产生纠葛。
     */
    private static final class ScaledCpu implements ICraftingCPU {
        private final ICraftingCPU inner;

        private ScaledCpu(ICraftingCPU inner) {
            if (inner == null) throw new IllegalArgumentException("inner CPU must not be null");
            this.inner = inner;
        }

        @Override
        public boolean isBusy() { return inner.isBusy(); }

        @Override
        public appeng.api.networking.crafting.CraftingJobStatus getJobStatus() { return inner.getJobStatus(); }

        @Override
        public void cancelJob() { inner.cancelJob(); }

        /** 分裂算力／无尽装配对容量的放大；委托取值后再走唯一放大入口。 */
        @Override
        public long getAvailableStorage() { return VirtualCpus.scaleStorage(inner.getAvailableStorage()); }

        /** 算力超频／分裂算力对并行处理单元数量的放大；委托取值后再走唯一放大入口。 */
        @Override
        public int getCoProcessors() { return VirtualCpus.scaleCoProcessors(inner.getCoProcessors()); }

        @Override
        public net.minecraft.network.chat.Component getName() { return inner.getName(); }

        @Override
        public appeng.api.config.CpuSelectionMode getSelectionMode() { return inner.getSelectionMode(); }

        @Override
        public String toString() { return "ScaledCpu[" + inner + "]"; }
    }
}
