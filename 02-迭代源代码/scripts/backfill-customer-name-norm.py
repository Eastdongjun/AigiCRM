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


def main():
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

    out = sys.stdout
    # 先写豁免，避免 name_norm 更新触发唯一索引瞬时冲突
    if dup_ids:
        out.write("UPDATE biz_customer SET dup_exempt=1 WHERE id IN ("
                  + ",".join(str(i) for i in sorted(dup_ids)) + ");\n")
    for r in rows:
        if r["norm"] is None:
            continue
        exempt = 1 if r["id"] in dup_ids else 0
        out.write(f"UPDATE biz_customer SET name_norm={sql_quote(r['norm'])}, "
                  f"dup_exempt={exempt} WHERE id={r['id']};\n")
    sys.stderr.write(f"rows={len(rows)} duplicate_exempt={len(dup_ids)}\n")


if __name__ == "__main__":
    main()
