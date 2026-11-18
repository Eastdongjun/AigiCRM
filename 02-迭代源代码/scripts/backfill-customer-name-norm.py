#!/usr/bin/env python3
"""客户名称归一化回填（与 CustomerNameNormalizer 同规则）。

用途：V2026.11.17.1 迁移里的 SQL 回填只能做 LOWER(TRIM)，无法做 NFKC/中文括号/全角
归一化；本脚本用与应用完全一致的规则重算 name_norm，并把活跃同名组中除最小 id 外的
行标记 dup_exempt=1（祖父条款，保留数据不删）。

输入：stdin 的 TSV，列顺序 id, tenant_id, customer_name, deleted（由 mysql -N 输出）。
输出：stdout 的 SQL（先标记重复豁免，再更新 name_norm），可直接管道给 mysql。

示例：
  mysql -N -e "SELECT id,tenant_id,REPLACE(customer_name,'\\t',' '),deleted FROM biz_customer" db \
    | python3 scripts/backfill-customer-name-norm.py \
    | mysql db
"""
import re
import sys
import unicodedata


def normalize(raw: str):
    if raw is None:
        return None
    s = unicodedata.normalize("NFKC", raw)
    s = (s.replace("​", "").replace("‌", "").replace("‍", "")
          .replace("﻿", "").replace("­", ""))
    s = (s.replace("【", "(").replace("】", ")")
          .replace("〔", "(").replace("〕", ")")
          .replace("〖", "(").replace("〗", ")")
          .replace("（", "(").replace("）", ")"))
    s = s.strip()
    s = re.sub(r"[\s 　]+", " ", s)
    s = s.lower()
    # 与应用一致：按 Unicode code point 计 100 字符；存量超长截断，避免列溢出
    return "".join(list(s)[:100])


def sql_quote(s: str) -> str:
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def parse_rows():
    rows = []
    for line in sys.stdin:
        line = line.rstrip("\n")
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        rid, tenant, name, deleted = parts[0], parts[1], parts[2], parts[3]
        if name in ("NULL", "\\N"):
            name = None
        rows.append({"id": int(rid), "tenant": tenant, "name": name,
                     "deleted": int(deleted), "norm": normalize(name)})
    return rows


def group_duplicate_ids(rows) -> set[int]:
    # 活跃同名组：按 tenant + norm 分组，id 最小者保留非豁免
    groups = {}
    for r in rows:
        if r["deleted"] == 0 and r["norm"]:
            groups.setdefault((r["tenant"], r["norm"]), []).append(r)
    dup_ids = set()
    for group in groups.values():
        group.sort(key=lambda x: x["id"])
        for r in group[1:]:
            dup_ids.add(r["id"])
    return dup_ids


def write_updates(out, rows, dup_ids) -> None:
    # 【2026-11-17 P2-7】按 500 行批量 CASE/IN，避免百万客户逐行 UPDATE 的往返与日志压力。
    BATCH = 500
    pending = [r for r in rows if r["norm"] is not None]
    for start in range(0, len(pending), BATCH):
        part = pending[start:start + BATCH]
        ids = ",".join(str(r["id"]) for r in part)
        norm_case = " ".join(
            f"WHEN {r['id']} THEN {sql_quote(r['norm'])}" for r in part)
        exempt_case = " ".join(
            f"WHEN {r['id']} THEN {1 if r['id'] in dup_ids else 0}" for r in part)
        out.write("UPDATE biz_customer SET "
                  f"name_norm=CASE id {norm_case} ELSE name_norm END, "
                  f"dup_exempt=CASE id {exempt_case} ELSE dup_exempt END "
                  f"WHERE id IN ({ids});\n")


def main():
    rows = parse_rows()
    dup_ids = group_duplicate_ids(rows)
    out = sys.stdout
    # 先写豁免，避免 name_norm 更新触发唯一索引瞬时冲突
    if dup_ids:
        out.write("UPDATE biz_customer SET dup_exempt=1 WHERE id IN ("
                  + ",".join(str(i) for i in sorted(dup_ids)) + ");\n")
    write_updates(out, rows, dup_ids)
    sys.stderr.write(f"rows={len(rows)} duplicate_exempt={len(dup_ids)}\n")


if __name__ == "__main__":
    main()
