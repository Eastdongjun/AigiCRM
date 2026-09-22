#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Git 增量审计(规范§7):changed files → 依赖影响 → 受影响模块 → 定向审计目标。
审计一个改动必须连同它的分层邻居(Controller/Service/Mapper/前端页面/测试)一起看。
子进程一律使用字面量参数列表(shell=False),不拼接任何命令。"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(os.getcwd()).resolve()
if not (ROOT / ".audit" / "config" / "audit.config.yaml").is_file():
    raise SystemExit("请在项目根目录运行")

SRC = "02-迭代源代码/"
LAYER_TOKENS = [
    (re.compile(r"Controller\.java$"), ["Service", "DTO", "VO"]),
    (re.compile(r"Service\.java$"), ["Controller", "Mapper", "Entity", "DTO", "Test"]),
    (re.compile(r"ServiceImpl\.java$"), ["Controller", "Mapper", "Entity", "Test"]),
    (re.compile(r"Mapper\.java$"), ["Service", "Entity", "xml"]),
    (re.compile(r"Entity\.java$"), ["Mapper", "Service"]),
]


def git_status_porcelain():
    out = subprocess.run(["git", "status", "--porcelain"], cwd=str(ROOT),
                         capture_output=True, text=True, timeout=120)
    return out.stdout


def git_ls_files():
    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files"],
                         cwd=str(ROOT), capture_output=True, text=True, timeout=120)
    return [p for p in out.stdout.splitlines() if p]


def changed_files():
    files = {"added": [], "modified": [], "deleted": [], "untracked": []}
    for line in git_status_porcelain().splitlines():
        if len(line) < 4:
            continue
        code, path = line[:2].strip(), line[3:].strip().strip('"')
        if code in ("A", "AM"):
            files["added"].append(path)
        elif code == "D":
            files["deleted"].append(path)
        elif code == "??":
            files["untracked"].append(path)
        else:
            files["modified"].append(path)
    return files


def impact_of(path):
    base = os.path.basename(path)
    stem = re.sub(r"(Controller|ServiceImpl|Service|Mapper|Entity|DTO|VO|Test)\.java$", "", base)
    stem = re.sub(r"\.(js|vue|ts|wxml|wxss|json|sql|sh|py|mjs)$", "", stem)
    hits = set()
    for rx, layers in LAYER_TOKENS:
        if rx.search(path):
            for layer in layers:
                hits.add((stem + layer + ".java").lower())
    if path.endswith(".java"):
        hits.add((stem + "test.java").lower())
        hits.add((stem + ".vue").lower())
        hits.add((stem + ".js").lower())
    if path.endswith((".vue", ".js")) and path.startswith(SRC + "aigicrm-ui"):
        hits.add((stem + ".java").lower())
    return hits


def main():
    files = changed_files()
    all_changed = files["added"] + files["modified"]
    tracked = git_ls_files()
    impacted = set()
    for path in all_changed:
        for want in impact_of(path):
            for t in tracked:
                tb = os.path.basename(t).lower()
                if want == tb or (want.endswith("test.java")
                                  and tb.startswith(want[: -len("test.java")]) and "test" in tb.lower()):
                    impacted.add(t)
    direct = list(all_changed)
    report = {
        "changed": files,
        "impact_expansion": sorted(impacted - set(direct)),
        "audit_targets": sorted(set(direct) | impacted),
        "summary": {"added": len(files["added"]), "modified": len(files["modified"]),
                    "deleted": len(files["deleted"]), "untracked": len(files["untracked"]),
                    "expanded_targets": len(impacted - set(direct))},
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
