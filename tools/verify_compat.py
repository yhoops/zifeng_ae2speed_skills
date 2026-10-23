"""Non-game regression checks. Does not start Minecraft or apply runtime mixins.
Usage: python tools/verify_compat.py [AdvancedAE.jar ExtendedAE.jar ExtendedAE-Plus.jar]
"""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None


def java_tool(name):
    executable = name + (".exe" if os.name == "nt" else "")
    if JAVA is not None:
        return str(JAVA / executable)
    resolved = shutil.which(executable)
    if resolved is None:
        raise RuntimeError(f"JDK tool not found: {name}. Set JAVA_HOME or add JDK 21 to PATH.")
    return resolved


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


# Compile only production effect/budget classes: no Minecraft runtime required.
with tempfile.TemporaryDirectory(prefix="ae2-compat-") as temporary:
    temp = Path(temporary)
    source = temp / "EffectChecks.java"
    source.write_text('''
import java.util.UUID;
import org.zifeng.ae2skills.Ae2Effects;
import org.zifeng.ae2skills.TickBudget;
public class EffectChecks {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        UUID player = new UUID(1, 2);
        Ae2Effects.clear();
        check(Ae2Effects.scaledCpuStorage(1024) == 1024);
        check(Ae2Effects.scaledCpuCoProcessors(10000) == 10000);
        for (int level = 1; level <= 4; level++) {
            Ae2Effects.update(player, level, level, level, level, 0);
            check(Ae2Effects.getAssemblerSpeedMultiplier() == 1 + level);
            check(Ae2Effects.scaledCpuCoProcessors(3) == 4 * (1 + level) - 1);
            check(Ae2Effects.scaledCpuCoProcessors(10000) >= 10000);
            check(Ae2Effects.scaledCpuStorage(1024) == 1024L * (1L << level));
            check(Ae2Effects.scaledCpuStorage(Long.MAX_VALUE / 2 + 1) == Long.MAX_VALUE);
            check(Ae2Effects.getInstantOpsPerTick() == 1 << level);
        }
        Ae2Effects.update(player, 4, 4, 5, 5, 0);
        check(Ae2Effects.scaledCpuStorage(1024) == Long.MAX_VALUE);
        check(Ae2Effects.scaledCpuCoProcessors(3) == Integer.MAX_VALUE - 1);
        int[] divine = {1, 2, 8, 16, 128, 1024};
        for (int level = 0; level <= 5; level++) {
            Ae2Effects.update(player, 0, 0, 0, 0, level);
            check(Ae2Effects.getDivineMultiplier() == divine[level]);
        }
        Ae2Effects.remove(player);
        check(Ae2Effects.scaledCpuStorage(1024) == 1024);
        check(Ae2Effects.scaledCpuCoProcessors(10000) == 10000);
        TickBudget.reset();
        TickBudget.markTickStart();
        long now = System.nanoTime();
        check(TickBudget.deadline(now) >= now);
        check(TickBudget.deadline(now + 50_000_000L) == now + 50_000_000L);
        System.out.println("PASS: skill levels, divine multipliers, native high-performance preservation, saturation, downgrade and budget");
    }
}
''', encoding="utf-8")
    # Minimal stubs for the only Minecraft types the checked classes touch (SkillDefinitions.icon).
    stub_item = temp / "Item.java"
    stub_item.write_text("package net.minecraft.world.item; public class Item {}", encoding="utf-8")
    stub_items = temp / "Items.java"
    stub_items.write_text(
        "package net.minecraft.world.item;\n"
        "public class Items {\n"
        "    public static final Item CRAFTER = new Item();\n"
        "    public static final Item COMPARATOR = new Item();\n"
        "    public static final Item END_CRYSTAL = new Item();\n"
        "    public static final Item NETHER_STAR = new Item();\n"
        "    public static final Item AMETHYST_CLUSTER = new Item();\n"
        "}\n", encoding="utf-8")
    run(java_tool("javac"), "-d", str(temp), str(source),
        str(ROOT / "src/main/java/org/zifeng/ae2skills/Ae2Effects.java"),
        str(ROOT / "src/main/java/org/zifeng/ae2skills/SkillDefinitions.java"),
        str(ROOT / "src/main/java/org/zifeng/ae2skills/TickBudget.java"),
        str(stub_item), str(stub_items))
    print(run(java_tool("java"), "-cp", str(temp), "EffectChecks").strip())

version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
               if line.startswith("mod_version="))
artifact = ROOT / f"build/libs/zifeng_ae2_skills-{version}.jar"
with zipfile.ZipFile(artifact) as jar:
    names = set(jar.namelist())
    for config_name in ("zifeng_ae2_skills.mixins.json", "zifeng_ae2_skills.compat.mixins.json"):
        config = json.loads(jar.read(config_name))
        for name in config["mixins"]:
            class_name = config["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class"
            assert class_name in names, f"Unpackaged mixin: {class_name}"
    assert "org/zifeng/ae2skills/mixin/compat/UfoCpuPoolSkillsMixin.class" in names
    print("PASS: required/optional mixin registrations including UFO pool adapter")

import sys
if len(sys.argv) > 1:
    targets = [
        (sys.argv[1], "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster",
         ["recalculateRemainingStorage();", "getRemainingCapacityCPU();", "getCoProcessors();"]),
        (sys.argv[1], "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",
         ["tickCraftingLogic(", "executeCrafting(", "getInventory();", "getElapsedTimeTracker();"]),
        (sys.argv[2], "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter",
         ["IGridTickable", "tickingRequest("]),
        (sys.argv[3], "com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity",
         ["IGridTickable", "tickingRequest("]),
    ]
    for file, cls, signatures in targets:
        output = run(java_tool("javap"), "-classpath", file, "-p", cls)
        assert all(signature in output for signature in signatures), f"Changed contract: {cls}"
    print("PASS: supplied AdvancedAE/ExtendedAE/ExtendedAE-Plus bytecode contracts")
print("NOT CHECKED: actual Mixin transformation and in-game item/power/lifecycle behavior")
