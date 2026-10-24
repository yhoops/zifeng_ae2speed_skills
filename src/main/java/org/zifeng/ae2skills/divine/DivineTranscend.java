package org.zifeng.ae2skills.divine;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.SkillDefinitions;
import org.zifeng.ae2skills.TickBudget;
import org.zifeng.ae2skills.crafting.CraftingDelivery;
import org.zifeng.ae2skills.crafting.JobAccess;
import org.zifeng.ae2skills.crafting.NativeCpuExecution;
import org.zifeng.ae2skills.crafting.TaskAccess;
import org.zifeng.ae2skills.mixin.ae2.CpuLogicAccess;

/**
 * 「万机之神的加护」等级 6：<b>瞬成</b>。
 *
 * <h2>一句话规则</h2>
 * 一个样板要跑很多批时，先照常让机器做 <b>1 批</b>；如果这一批的预期产物真的回到了合成任务
 * （{@code waitingFor} 被接纳），就把这个样板剩下的全部批次一次性记账完成——材料从合成 CPU 的
 * 内部库存扣掉，产物按 AE2 原生路径直接投递，<b>不再需要机器</b>，也不再扣电。
 *
 * <h2>为什么必须「先做一批再放行」</h2>
 * 样板本身没有任何信息能证明它真的能产出：空壳机器、吞掉材料的机器、配方写错但仍能被推送的
 * 样板，{@code pushPattern} 全都会返回 {@code true}。只有 {@code CraftingCpuLogic.insert}
 * 真的接纳了该样板的产物，才说明「这条路走得通」。所以验证判据取的是<b>产物回流</b>，
 * 而不是「推送成功」——宽松判定会让这个技能变成凭空刷物品。
 *
 * <h2>为什么验证期间要锁住该样板</h2>
 * AE2 的 {@code executeCrafting} 在一个 tick 内会反复推同一样板（只要供应器不 busy），
 * 一个 tick 就能推出很多批。若不锁，验证还没结果就已经有 N 批在飞，此时「按剩余批数结清」
 * 会把这 N 批重复记账，直接超产。所以从第一次推送成功到验证出结果之间，本类对该
 * (合成 CPU, 样板) 对的后续推送一律否决（见 {@code PatternProviderPushMixin}），
 * 验证一结束立刻放开。
 *
 * <h2>时序</h2>
 * <ol>
 *   <li>{@code executeCrafting} 期间推送成功 → 记账 PENDING 并上锁；</li>
 *   <li>机器照常加工这一批（照吃 5 级的 ×1024 加速）；</li>
 *   <li>产物回流被 {@code CraftingCpuLogic.insert} 接纳 → 标 VERIFIED；</li>
 *   <li>下一次 {@code tickCraftingLogic} → 结算剩余批次（<b>不在 insert 调用栈里重入</b>）；</li>
 *   <li>{@value #VERIFY_TIMEOUT_TICKS} tick 内没等到产物 → 放弃该样板，永久不再尝试，
 *       回落 5 级加速（避免空壳机的任务被反复卡住）。</li>
 * </ol>
 *
 * <h2>边界</h2>
 * 只接管「处理样板」；合成台样板仍归「无尽装配」（{@code InstantCrafting}），那里本来就是瞬成。
 * 只对 AE2 原生合成 CPU 生效：第三方自有 CPU（形态 b）不产生 {@code ExecutingCraftingJob}，
 * 拿不到任务与批次计数。
 */
public final class DivineTranscend {
    /** 验证窗口：约 10 秒（200 tick）。超时即放弃该样板的瞬成资格。 */
    private static final int VERIFY_TIMEOUT_TICKS = 200;
    /** 结算时间片：2.5ms，与万机之神加速同量级；超出的批次留到下一 tick 继续。 */
    private static final long SETTLE_ALLOWANCE_NANOS = 2_500_000L;

    private static final int PENDING = 0;
    private static final int VERIFIED = 1;

    /** 一个 (合成 CPU, 样板) 对的取证明细。 */
    private static final class Entry {
        final IPatternDetails pattern;
        final Set<AEKey> expected;
        final long startedTick;
        int state = PENDING;
        int stalls;

        Entry(IPatternDetails pattern, Set<AEKey> expected, long startedTick) {
            this.pattern = pattern;
            this.expected = expected;
            this.startedTick = startedTick;
        }
    }

    /** 一台合成 CPU 的账本；换任务即整本作废。 */
    private static final class Ledger {
        Object job;
        final List<Entry> entries = new ArrayList<>();
        /** 已经放弃过的样板：同一个任务里不再重复尝试（否则超时后会一推一验、无限循环）。 */
        final Set<IPatternDetails> dead = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        long lastTick = Long.MIN_VALUE;
    }

    /**
     * 键是弱引用：CPU 被拆掉、集群解散或区块卸载后不再 tick，用强引用会把
     * {@code CraftingCpuLogic → CraftingCPUCluster → Level} 整条链钉在静态表里永不释放。
     */
    private static final Map<CraftingCpuLogic, Ledger> LEDGERS = new java.util.WeakHashMap<>();
    /** 正在执行 {@code executeCrafting} 的 CPU；推送发生在它的调用栈里，用它能定位账本。 */
    private static CraftingCpuLogic pushing;
    /** {@code insert} 调用的入参快照栈（产物回流是单层调用，用栈只为异常时不留脏值）。 */
    private static final ThreadLocal<ArrayDeque<Object[]>> INSERT_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private DivineTranscend() {}

    public static void reset() {
        LEDGERS.clear();
        pushing = null;
    }

    /** 是否已达到瞬成等级。 */
    public static boolean unlocked() {
        return Ae2Effects.getDivineLevel() >= SkillDefinitions.DIVINE_MAX_LEVEL;
    }

    // ------------------------------------------------------------------ 推送侧

    public static void enterPush(CraftingCpuLogic logic) { pushing = logic; }

    public static void exitPush() { pushing = null; }

    /** 每个 tick 起点兜底清空：推送过程中抛异常时 {@code exitPush} 不会执行。 */
    public static void resetPushing() { pushing = null; }

    /**
     * 该样板此刻是否应当被拦住。
     * <p>拦住 = 这个 (CPU, 样板) 对正在取证，或已取证完但还没结算完。拦住只是让
     * {@code pushPattern} 返回 false（AE2 会原样跳过这个供应器），不影响该任务的其它样板。
     */
    public static boolean vetoPush(IPatternDetails pattern) {
        CraftingCpuLogic logic = pushing;
        if (logic == null || pattern == null) return false;
        Ledger ledger = LEDGERS.get(logic);
        if (ledger == null || ledger.entries.isEmpty()) return false;
        for (Entry entry : ledger.entries) {
            if (entry.pattern == pattern) return true;
        }
        return false;
    }

    /**
     * {@code insert} 进入时记下该键此刻的 waitingFor，用来算「这一调用真的被合成任务吃掉了多少」。
     * <p>不能用 {@code insert} 的返回值：它返回的是<b>下游领取方</b>（{@code job.link}）拿走多少，
     * 无人类领取、产物直接进 ME 存储的订单恒返回 0，而 waitingFor 其实已经被扣掉了。
     */
    public static void beginInsert(CraftingCpuLogic logic, AEKey key) {
        INSERT_STACK.get().push(new Object[]{logic, key, logic.getWaitingFor(key)});
    }

    /** 本次 {@code insert} 调用里，合成任务真的从 waitingFor 里扣掉了多少。 */
    private static long acceptedByJob(CraftingCpuLogic logic, AEKey key) {
        ArrayDeque<Object[]> stack = INSERT_STACK.get();
        if (stack.isEmpty()) return 0;
        Object[] frame = stack.pop();
        if (frame[0] != logic || frame[1] != key) return 0;
        return Math.max(0L, (long) frame[2] - logic.getWaitingFor(key));
    }

    /** 样板被成功推给机器（{@code pushPattern} 返回 true）之后调用：开一份取证。 */
    public static void onPushed(IPatternDetails pattern) {
        CraftingCpuLogic logic = pushing;
        if (logic == null || pattern == null) return;
        if (!unlocked()) return;
        // 合成台样板归「无尽装配」；6 级只接管处理样板。
        if (pattern instanceof IMolecularAssemblerSupportedPattern) return;
        Ledger ledger = LEDGERS.get(logic);
        if (ledger == null) {
            Object job = ((CpuLogicAccess) logic).zifeng$getJob();
            if (job == null) return;
            ledger = new Ledger();
            ledger.job = job;
            LEDGERS.put(logic, ledger);
        }
        if (ledger.dead.contains(pattern)) return;
        for (Entry entry : ledger.entries) {
            if (entry.pattern == pattern) return;
        }
        Set<AEKey> expected = new HashSet<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output != null && output.what() != null) expected.add(output.what());
        }
        if (expected.isEmpty()) return;
        ledger.entries.add(new Entry(pattern, expected, currentTick(logic)));
    }

    // ------------------------------------------------------------------ 验证侧

    /**
     * {@code CraftingCpuLogic.insert} 真的接纳了产物之后调用（严格判据）。
     * <p>排除本模组自己的投递：那是「无尽装配」或本类结算产生的，不代表机器验证过。
     */
    public static void onInsert(CraftingCpuLogic logic, AEKey key, Actionable mode) {
        if (mode != Actionable.MODULATE) return;
        long accepted = acceptedByJob(logic, key);
        if (accepted <= 0) return;
        if (CraftingDelivery.isDelivering()) return;
        Ledger ledger = LEDGERS.get(logic);
        if (ledger == null || ledger.entries.isEmpty()) return;
        Entry match = null;
        for (Entry entry : ledger.entries) {
            if (entry.state != PENDING || !entry.expected.contains(key)) continue;
            // 两条样板产同一种东西时，光凭 key 分不清是谁的机器送回来的。
            // 此时不猜：宁可让这两条都退回 5 级速度，也不能替没验证过的那条结清。
            if (match != null) return;
            match = entry;
        }
        if (match != null) match.state = VERIFIED;
    }

    // ------------------------------------------------------------------ 结算侧

    public static void tick(CraftingCpuLogic logic) {
        if (!unlocked()) {
            if (!LEDGERS.isEmpty()) LEDGERS.remove(logic);
            return;
        }
        CpuLogicAccess access = (CpuLogicAccess) logic;
        Object job = access.zifeng$getJob();
        Ledger ledger = LEDGERS.get(logic);
        if (job == null) {
            if (ledger != null) LEDGERS.remove(logic);
            return;
        }
        if (ledger == null) {
            ledger = new Ledger();
            ledger.job = job;
            LEDGERS.put(logic, ledger);
        } else if (ledger.job != job) {
            // 换任务：旧任务的取证全部作废，绝不跨任务结算。
            ledger.job = job;
            ledger.entries.clear();
            ledger.dead.clear();
        }
        if (ledger.entries.isEmpty()) return;
        if (logic.isJobSuspended() || !access.zifeng$getCluster().isActive()) return;
        long now = currentTick(logic);
        // 同一 tick 的重复入口（原生 tick 可能被多个入口驱动）只结算一次。
        if (now != Long.MIN_VALUE && ledger.lastTick == now) return;
        ledger.lastTick = now;

        NativeCpuExecution exec = new NativeCpuExecution(logic);
        JobAccess jobAccess = (JobAccess) job;
        Map<IPatternDetails, Object> tasks = jobAccess.zifeng$getTasks();
        long began = System.nanoTime();
        long deadline = TickBudget.deadline(began, SETTLE_ALLOWANCE_NANOS);
        try {
            for (Iterator<Entry> it = ledger.entries.iterator(); it.hasNext(); ) {
                Entry entry = it.next();
                // 上一轮的投递可能刚好把任务做完（AE2 在最终产物被全额接受时立刻 finishJob），
                // 之后的 entry 都属于已经死掉的任务，再动它们只会把账记到空处。
                if (exec.skillJob() != job) break;
                Object progress = tasks.get(entry.pattern);
                if (!(progress instanceof TaskAccess task)) { it.remove(); continue; }
                if (entry.state == PENDING) {
                    if (now != Long.MIN_VALUE && entry.startedTick != Long.MIN_VALUE
                            && now - entry.startedTick >= VERIFY_TIMEOUT_TICKS) {
                        // 超时：放弃这条样板，解锁并本任务内不再尝试，回落 5 级加速。
                        ledger.dead.add(entry.pattern);
                        it.remove();
                    }
                    continue;
                }
                if (System.nanoTime() >= deadline) break;
                long committed = settle(exec, jobAccess, task, entry);
                if (committed > 0) {
                    // 有进展就清零：材料分批送达（子合成还没做完）不算失败，否则大单会被误判成
                    // 「结不动」而永久退回 5 级。
                    entry.stalls = 0;
                    if (task.zifeng$getRemaining() <= 0) {
                        // 批次结清后把样板从任务表里摘掉；AE2 自己也会在下一轮 executeCrafting
                        // 里清理归零的条目，这里同步一次只是让「任务真的做完了」立刻可见。
                        tasks.remove(entry.pattern);
                        it.remove();
                    }
                    continue;
                }
                if (committed == 0) {
                    tasks.remove(entry.pattern);
                    it.remove();
                    continue;
                }
                if (++entry.stalls >= VERIFY_TIMEOUT_TICKS) {
                    ledger.dead.add(entry.pattern);
                    it.remove();
                }
            }
        } finally {
            TickBudget.charge(began);
        }
    }

    /**
     * 把该样板剩下的批次一次记账完成。<b>免电</b>：这里刻意不算、也不扣
     * {@code CraftingCpuHelper.calculatePatternPower}——机器根本没参与这些批次。
     *
     * @return 本次提交的批数；{@code 0} 表示这条取证本就该结束（批次已归零）；
     *         {@code -1} 表示这次提交不了（材料不够 / 批次算成 0），下一 tick 再试
     */
    private static long settle(NativeCpuExecution exec, JobAccess job, TaskAccess task, Entry entry) {
        long remaining = task.zifeng$getRemaining();
        if (remaining <= 0) return 0;
        ListCraftingInventory inventory = exec.skillInventory();
        KeyCounter outputs = new KeyCounter();
        KeyCounter containers = new KeyCounter();
        // AE2 负责校验替换物并算出真实的容器键；这一批的输入在这里就已经被扣掉了。
        KeyCounter[] inputs = CraftingCpuHelper.extractPatternInputs(entry.pattern, inventory,
                exec.skillLevel(), outputs, containers);
        if (inputs == null) return -1;   // 材料还不够（子合成未完），下一 tick 再试
        boolean committed = false;
        try {
            KeyCounter consumed = new KeyCounter();
            for (KeyCounter input : inputs) consumed.addAll(input);
            long count = remaining;
            for (var input : consumed) {
                long each = input.getLongValue();
                if (each <= 0) continue;
                // 本模组有无限容量类技能，库存值可以顶到 Long.MAX_VALUE；+1 之前先挡溢出，
                // 否则会算成负数并被当成「提交不了」，表现为该样板莫名其妙不瞬成。
                long affordable = exec.skillStored(input.getKey()) / each;
                if (affordable < Long.MAX_VALUE - 1) count = Math.min(count, affordable + 1);
            }
            KeyCounter produced = new KeyCounter();
            produced.addAll(outputs);
            produced.addAll(containers);
            for (var output : produced) {
                long each = output.getLongValue();
                if (each <= 0) continue;
                // 所有 AE2 计数都是有符号 long，乘加之前先把批次封顶。
                long pending = job.zifeng$getWaitingFor().list.get(output.getKey());
                count = Math.min(count, (Long.MAX_VALUE - Math.max(0, pending)) / each);
                count = Math.min(count, (Long.MAX_VALUE - Math.max(0, exec.skillStored(output.getKey()))) / each);
            }
            if (count <= 0) return -1;
            // extractPatternInputs 已经扣掉 1 批，这里补上其余 count-1 批。
            for (var input : consumed) {
                inventory.extract(input.getKey(), input.getLongValue() * (count - 1), Actionable.MODULATE);
            }
            task.zifeng$setRemaining(remaining - count);
            committed = true;
            for (var remainder : containers) {
                exec.skillAddMaxItems(remainder.getLongValue() * count, remainder.getKey().getType());
            }
            for (var output : produced) {
                job.zifeng$getWaitingFor().insert(output.getKey(), output.getLongValue() * count, Actionable.MODULATE);
            }
            GenericStack finalOutput = exec.skillFinalOutput();
            AEKey finalKey = finalOutput == null ? null : finalOutput.what();
            // 容器/副产物先投递，最终产物最后——否则任务可能在副产物回来之前就完成。
            for (var output : produced) {
                if (finalKey == null || !output.getKey().equals(finalKey)) {
                    CraftingDelivery.deliver(exec, output.getKey(), output.getLongValue() * count);
                }
            }
            if (finalKey != null) {
                long finalAmount = produced.get(finalKey) * count;
                if (finalAmount > 0) CraftingDelivery.deliver(exec, finalKey, finalAmount);
            }
            exec.skillMarkDirty();
            return count;
        } finally {
            if (!committed) CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
        }
    }

    private static long currentTick(CraftingCpuLogic logic) {
        Level level = ((CpuLogicAccess) logic).zifeng$getCluster().getLevel();
        return level instanceof ServerLevel server ? server.getServer().getTickCount() : Long.MIN_VALUE;
    }
}
