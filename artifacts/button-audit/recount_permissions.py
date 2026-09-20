#!/usr/bin/env python3
"""权限码消费方权威统计(2026-09-20 复检修正版)

修正初版方法学错误: 初版只用 `grep -F "'<码>'"` 匹配单引号, 漏掉后端大量存在的
`SecurityUtils.hasAuthority("xxx")` 双引号调用, 导致 2 条结论失真(D-04 多报 1 个、
D-05 整条误判)。

本脚本口径:
  - 同时匹配单引号与双引号
  - 剔除 `//`、`*`、`/*` 开头的注释行
  - 分三类计数: @PreAuthorize 注解 / 方法体 hasAuthority 代码 / 仅注释提及

用法: python3 artifacts/button-audit/recount_permissions.py
退出码: 0
"""
import argparse
import json
import re
from pathlib import Path

_ap = argparse.ArgumentParser()
_ap.add_argument("--project-root", default=None,
                 help="AIGICRM 项目根目录(默认取脚本所在位置向上两级)")
ARGS = _ap.parse_args()

ROOT = Path(ARGS.project_root).resolve() if ARGS.project_root \
    else Path(__file__).resolve().parents[2]
BE = ROOT / "02-迭代源代码/aigicrm-backend/src/main/java"
# 台账可能与本脚本同目录(项目内), 也可能位于中央 evidence 目录
INV = Path(__file__).resolve().parent / "static_inventory.json"
if not INV.exists():
    INV = ROOT / "artifacts/button-audit/static_inventory.json"

# fail-fast: 避免在错误根目录下静默得出"全部都是死码"的假结论
if not BE.is_dir():
    raise SystemExit(
        "ERROR: 后端源码目录不存在: %s\n"
        "       脚本似乎不在项目内运行。请用 --project-root <AIGICRM 项目根> 指定。" % BE)
if not INV.exists():
    raise SystemExit("ERROR: 静态台账不存在: %s\n       请先运行 static_inventory.py" % INV)

CODE_LOOKUP = re.compile(
    r"""hasAuthority\s*\(\s*["']([a-z][a-z0-9:_-]*)["']"""      # hasAuthority('x') / ("x")
    r"""|hasAnyAuthority\s*\(([^)]*)\)"""                        # hasAnyAuthority('a','b')
    r"""|@PreAuthorize\s*\(\s*["']([^"']*)["']""")              # @PreAuthorize("hasAuthority('x')")


def is_comment(line: str) -> bool:
    s = line.strip()
    return s.startswith("//") or s.startswith("*") or s.startswith("/*")


def scan_backend():
    """返回 code -> {'annotation': n, 'body': n, 'comment': n, 'where': [..]}"""
    found = {}

    def add(code, kind, loc):
        rec = found.setdefault(code, {"annotation": 0, "body": 0, "comment": 0, "where": []})
        rec[kind] += 1
        if loc not in rec["where"]:
            rec["where"].append(loc)

    for path in BE.rglob("*.java"):
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        for i, line in enumerate(lines, 1):
            loc = "%s:%d" % (path.name, i)
            # 注释行: 只记 comment, 不参与"有无消费方"判定
            if is_comment(line):
                for c in re.findall(r"[a-z][a-z0-9:_-]*:[a-z][a-z0-9:_-]*", line):
                    if c in MENU_CODES:
                        add(c, "comment", loc)
                continue
            if "@PreAuthorize" in line:
                for m in re.findall(r"""["']([a-z][a-z0-9:_-]*)["']""", line):
                    add(m, "annotation", loc)
                continue
            if "hasAuthority" in line or "hasAnyAuthority" in line:
                for m in CODE_LOOKUP.finditer(line):
                    g1, g2, g3 = m.group(1), m.group(2), m.group(3)
                    if g1:
                        add(g1, "body", loc)
                    elif g2:
                        for c in re.findall(r"""["']([a-z][a-z0-9:_-]*)["']""", g2):
                            add(c, "body", loc)
                    elif g3:
                        for c in re.findall(r"""["']([a-z][a-z0-9:_-]*)["']""", g3):
                            add(c, "annotation", loc)
    return found


def scan_frontend():
    hits = {}
    for base in [ROOT / "02-迭代源代码/aigicrm-ui/src",
                 ROOT / "02-迭代源代码/aigicrm-miniprogram"]:
        for p in list(base.rglob("*.vue")) + list(base.rglob("*.js")):
            if "node_modules" in str(p):
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            for c in MENU_CODES:
                if c in text:
                    hits.setdefault(c, set()).add(p.name)
    return hits


inv = json.loads(INV.read_text(encoding="utf-8"))
MENU_CODES = {m["permission"] for m in inv["menus"] if m["permission"]}

backend = scan_backend()
frontend = scan_frontend()

referenced = {c for c, r in backend.items() if r["annotation"] or r["body"]}
dead = sorted(c for c in MENU_CODES if c not in referenced and c not in frontend)
missing_from_dict = sorted(c for c in referenced if c not in MENU_CODES)

print("字典权限码总数        : %d" % len(MENU_CODES))
print("后端真实引用码数      : %d" % len(referenced))
print("后端引用但字典无此行  : %d  %s" % (len(missing_from_dict), missing_from_dict or ""))
print("真·死码(后端零引用且前端零引用): %d" % len(dead))
for c in dead:
    print("    %-26s 注释提及=%d" % (c, backend.get(c, {}).get("comment", 0)))
print()
print("仅前端引用的码:")
for c in sorted(MENU_CODES):
    if c not in referenced and c in frontend:
        print("    %-26s %s" % (c, sorted(frontend[c])))
