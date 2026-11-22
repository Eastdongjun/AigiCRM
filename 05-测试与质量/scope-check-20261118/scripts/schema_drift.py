#!/usr/bin/env python3
"""对比测试库实际 schema 与未应用迁移，输出缺失对象目录（只读）。"""
import os
import re

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(BASE, "data")
EVID = os.path.join(BASE, "evidence")
MIG = "/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-backend/src/main/resources/db/migration"
LATEST_APPLIED = "2026.11.16.9"

tables = {x.strip() for x in open(os.path.join(DATA, "tables.tsv"), encoding="utf-8") if x.strip()}
cols = {}
for line in open(os.path.join(DATA, "columns.tsv"), encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if len(f) >= 2:
        cols.setdefault(f[0], set()).add(f[1])


def vkey(v):
    return [int(x) for x in v.split(".")]


pending = []
for fn in sorted(os.listdir(MIG)):
    m = re.match(r"V([0-9.]+)__", fn)
    if m and vkey(m.group(1)) > vkey(LATEST_APPLIED):
        pending.append((m.group(1), fn))

rows = []
for ver, fn in pending:
    text = open(os.path.join(MIG, fn), encoding="utf-8").read()
    created = re.findall(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?", text, re.I)
    missing_tables = [t for t in created if t not in tables]
    alter_cols = []
    for block in re.split(r"ALTER TABLE", text, flags=re.I)[1:]:
        tm = re.match(r"\s*`?(\w+)`?", block)
        if not tm:
            continue
        table = tm.group(1)
        for cm in re.finditer(r"ADD COLUMN\s+`?(\w+)`?", block, re.I):
            alter_cols.append((table, cm.group(1)))
        for cm in re.finditer(r"CHANGE COLUMN\s+`?(\w+)`?\s+`?(\w+)`?", block, re.I):
            alter_cols.append((table, cm.group(2)))
        for cm in re.finditer(r"MODIFY COLUMN\s+`?(\w+)`?", block, re.I):
            alter_cols.append((table, cm.group(1)))
    missing_cols = []
    for t, c in alter_cols:
        if t not in tables or c not in cols.get(t, set()):
            missing_cols.append((t, c))
    rows.append((ver, fn, missing_tables, missing_cols))

with open(os.path.join(EVID, "schema-drift-20261118.md"), "w", encoding="utf-8") as f:
    f.write("# 测试库 Schema 漂移目录（未应用迁移）\n\n")
    f.write(f"- 测试库 Flyway 最新：`{LATEST_APPLIED}`\n")
    f.write(f"- 未应用迁移：{len(pending)} 个\n")
    f.write("- 说明：仅静态对比迁移文件与实际 information_schema；生产未触碰。\n\n")
    for ver, fn, mt, mc in rows:
        f.write(f"## {ver} — {fn}\n\n")
        if not mt and not mc:
            f.write("- 无缺失表/列（可能仅数据变更或索引）。\n\n")
            continue
        if mt:
            f.write(f"- 缺表：{', '.join('`'+x+'`' for x in mt)}\n")
        if mc:
            f.write("- 缺列：\n")
            for t, c in mc:
                f.write(f"  - `{t}.{c}`\n")
        f.write("\n")

print("pending", len(pending))
for ver, fn, mt, mc in rows:
    print(ver, fn, "missing_tables=", len(mt), "missing_cols=", len(mc))
    for t in mt:
        print("  TABLE", t)
    for t, c in mc:
        print("  COL", t, c)
