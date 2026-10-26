package org.zifeng.ae2skills.crafting;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import org.zifeng.ae2skills.TickBudget;

/** Unlimited co-processors remove the operation-count limit, never machine/input/power constraints. */
public final class UnlimitedDispatch {
    private UnlimitedDispatch() {}
    public static final class Cursor {
        private Object job;
        private List<IPatternDetails> patterns = List.of();
        private int next;
        public void clear() { job = null; patterns = List.of(); next = 0; }
    }
    public static void tick(CraftingCpuLogic logic, Cursor cursor, CraftingService service, IEnergyService energy) {
        tick(new NativeCpuExecution(logic), cursor, service, energy);
    }
    public static void tick(CpuExecution logic, Cursor cursor, CraftingService service, IEnergyService energy) {
        long began = System.nanoTime(), deadline = TickBudget.deadline(began);
        if (deadline <= began) return;
        var original = logic.skillJob();
        if (original == null) { cursor.clear(); return; }
        var job = (JobAccess) original;
        if (logic.skillSuspended() || job.zifeng$getLink().isCanceled() || !logic.skillActive()) return;
        try {
            if (cursor.job != original) {
                cursor.job = original;
                cursor.patterns = new ArrayList<>(job.zifeng$getTasks().keySet());
                cursor.next = 0;
            }
            int scanned = 0;
            boolean advanced = false;
            while (!cursor.patterns.isEmpty() && System.nanoTime() < deadline) {
                IPatternDetails pattern = cursor.patterns.get(cursor.next);
                cursor.next = (cursor.next + 1) % cursor.patterns.size();
                Object entry = job.zifeng$getTasks().get(pattern);
                if (entry instanceof TaskAccess task && task.zifeng$getRemaining() > 0) {
                    KeyCounter outputs = new KeyCounter(), containers = new KeyCounter();
                    var inputs = CraftingCpuHelper.extractPatternInputs(pattern, logic.skillInventory(), logic.skillLevel(), outputs, containers);
                    if (inputs != null) {
                        boolean pushed = false;
                        try {
                            double power = CraftingCpuHelper.calculatePatternPower(inputs);
                            if (energy.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= power - 0.01D) {
                                for (var provider : service.getProviders(pattern)) {
                                    if (System.nanoTime() >= deadline) break;
                                    if (provider.isBusy() || !provider.pushPattern(pattern, inputs)) continue;
                                    pushed = true;
                                    energy.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
                                    for (var output : outputs) job.zifeng$getWaitingFor().insert(output.getKey(), output.getLongValue(), Actionable.MODULATE);
                                    for (var remainder : containers) {
                                        job.zifeng$getWaitingFor().insert(remainder.getKey(), remainder.getLongValue(), Actionable.MODULATE);
                                        logic.skillAddMaxItems(remainder.getLongValue(), remainder.getKey().getType());
                                    }
                                    task.zifeng$setRemaining(task.zifeng$getRemaining() - 1);
                                    logic.skillMarkDirty();
                                    advanced = true;
                                    if (task.zifeng$getRemaining() == 0) job.zifeng$getTasks().remove(pattern);
                                    break;
                                }
                            }
                        } finally {
                            if (!pushed) CraftingCpuHelper.reinjectPatternInputs(logic.skillInventory(), inputs);
                        }
                    }
                }
                if (logic.skillJob() != original) { cursor.clear(); return; }
                if (++scanned >= cursor.patterns.size()) {
                    if (!advanced) break;
                    scanned = 0; advanced = false;
                }
            }
        } finally { TickBudget.charge(began); }
    }
}
