#!/usr/bin/env python3
"""Witness policy failures in disposable copies; never change the active module builds."""
from __future__ import annotations

import datetime
import hashlib
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAMP = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
OUT = ROOT / "evidence" / "module-boundaries" / STAMP
OUT.mkdir(parents=True)
(OUT / "executed-check-module-boundaries.py").write_bytes(Path(__file__).read_bytes())
CHECKS: list[dict] = []


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# Deliberately scope preservation to the files these probes could otherwise mutate.
# Other agents may be reviewing Android source/resources concurrently.
policy_files = [ROOT / "build.gradle.kts", ROOT / "settings.gradle.kts"]
policy_files += [p for p in ROOT.glob("**/build.gradle.kts")
                 if p != ROOT / "build.gradle.kts" and "build" not in p.relative_to(ROOT).parts]
baseline = {str(p.relative_to(ROOT)): sha256(p) for p in policy_files}


def copy_project() -> Path:
    target = Path(tempfile.mkdtemp(prefix="common-goods-policy-"))
    shutil.copytree(
        ROOT, target, dirs_exist_ok=True,
        ignore=shutil.ignore_patterns("build", ".gradle", ".kotlin", "evidence", "__pycache__", "evidence-*.log"),
    )
    return target


def run(target: Path, name: str, expect_success: bool, required: str) -> None:
    command = [str(target / "gradlew"), "verifyModuleBoundaries", "--offline", "--console=plain"]
    log_path = OUT / f"{name}.log"
    with log_path.open("w") as log:
        result = subprocess.run(command, cwd=target, stdout=log, stderr=subprocess.STDOUT, check=False)
    output = log_path.read_text()
    passed = (result.returncode == 0) == expect_success and required in output
    CHECKS.append({
        "name": name, "command": command, "exit_code": result.returncode,
        "expected": "success" if expect_success else "policy rejection",
        "required_diagnostic": required, "passed": passed,
        "log": str(log_path.relative_to(ROOT)), "log_sha256": sha256(log_path),
    })
    report = target / "build/reports/module-boundaries.txt"
    if report.exists():
        shutil.copy2(report, OUT / f"{name}-module-edges.txt")
    if not passed:
        raise AssertionError(f"{name} did not produce its expected witness:\n{output[-4000:]}")
    print(f"PASS {name}", flush=True)


def append(target: Path, relative: str, text: str) -> None:
    path = target / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a") as file:
        file.write(text)


try:
    work = copy_project()
    try:
        run(work, "baseline", True, "COMMON GOODS MODULE BOUNDARIES PASS: 7 modules, 13 declared edges")
        run(work, "configuration-cache-reuse", True, "Configuration cache entry reused")
        append(work, "feature/saved/build.gradle.kts", '\ndependencies { implementation(project(":core:data")) }\n')
        run(work, "feature-storage-leak", False, ":feature:saved|implementation|:core:data")
    finally:
        shutil.rmtree(work)

    work = copy_project()
    try:
        append(work, "feature/cart/build.gradle.kts", '\ndependencies { releaseImplementation(project(":feature:catalog")) }\n')
        run(work, "release-cross-feature", False, ":feature:cart|releaseImplementation|:feature:catalog")
    finally:
        shutil.rmtree(work)

    work = copy_project()
    try:
        append(work, "feature/catalog/build.gradle.kts", '\nval escapeHatch by configurations.creating\ndependencies { add("escapeHatch", project(":feature:cart")) }\n')
        run(work, "custom-configuration", False, ":feature:catalog|escapeHatch|:feature:cart")
    finally:
        shutil.rmtree(work)

    work = copy_project()
    try:
        append(work, "settings.gradle.kts", '\ninclude(":feature:unchecked")\n')
        append(work, "feature/unchecked/build.gradle.kts", "// Deliberately unreviewed project.\n")
        run(work, "unknown-module", False, "MODULE REGISTRY REQUIRES REVIEW: extra=[:feature:unchecked]")
    finally:
        shutil.rmtree(work)

    work = copy_project()
    try:
        append(work, "core/build.gradle.kts", '\nplugins { `java-library` }\n')
        run(work, "namespace-becomes-module", False, "NAMESPACE REQUIRES REVIEW")
    finally:
        shutil.rmtree(work)
finally:
    after = {relative: sha256(ROOT / relative) for relative in baseline}
    preserved = after == baseline
    (OUT / "result.json").write_text(json.dumps({
        "passed": len(CHECKS) == 7 and all(row["passed"] for row in CHECKS) and preserved,
        "checks": CHECKS,
        "active_build_files_preserved": preserved,
        "active_build_file_hashes": baseline,
        "scope": "Policy task only; no app builds, tests, installation or device commands.",
    }, indent=2) + "\n")
    print(OUT, flush=True)
    if not preserved:
        raise AssertionError("Active build-file hashes changed during probes; inspect before accepting evidence.")
