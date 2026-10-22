"""Validate addon translations and prove the distributable contains no host/test classes."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

root = Path(__file__).resolve().parents[1]


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate language key: {key}")
        result[key] = value
    return result


languages = []
for name in ("zh_cn", "en_us"):
    path = root / f"src/main/resources/assets/zifeng_ae2_skills/lang/{name}.json"
    languages.append(json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object))
assert languages[0].keys() == languages[1].keys(), "Language key mismatch"

version = next(line.split("=", 1)[1].strip() for line in (root / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
artifact = Path(sys.argv[1]) if len(sys.argv) > 1 else root / f"build/libs/zifeng_ae2_skills-{version}.jar"
with zipfile.ZipFile(artifact) as jar:
    names = jar.namelist()
    assert "META-INF/neoforge.mods.toml" in names
    assert "zifeng_ae2_skills.mixins.json" in names
    assert not any(name.startswith(("org/zifeng/skilltree/", "appeng/")) for name in names), "Host/AE2 code bundled"
    assert not any("/test/" in name or name.endswith("legacy-1.5.3.nbt") or "/structure/empty.nbt" in name for name in names), "Test fixture bundled"
    contract = jar.read("zifeng-ae2-contract-classes.txt").decode().splitlines()
    assert contract and all(name + ".class" in names and "/test/" not in name for name in contract)
    classes = {name.removesuffix(".class") for name in names if name.endswith(".class")}
    assert len(contract) == len(set(contract)) and set(contract) == classes, "Incomplete compatibility class index"
    metadata = jar.read("META-INF/neoforge.mods.toml").decode()
    assert 'versionRange="[21.1.234,21.2)"' in metadata
    assert 'versionRange="[1.21.1]"' in metadata
    assert metadata.count('versionRange="[0,)"') == 2

print(f"PASS: {len(languages[0])} matching language keys; addon-only jar; all compatibility class resources present")
print(f"Artifact: {artifact.name}; {artifact.stat().st_size} bytes")
print(f"SHA-256: {hashlib.sha256(artifact.read_bytes()).hexdigest()}")
