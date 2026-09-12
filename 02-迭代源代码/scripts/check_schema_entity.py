#!/usr/bin/env python3
"""Cross-check Java MyBatis-Plus entities against SQL CREATE TABLE definitions.

Catches the bug classes:
  - entity field has no matching DB column (INSERT/UPDATE will fail at runtime)
  - entity String field maps to a numeric DB column (e.g. storing "[1,2]" in a BIGINT)
  - entity numeric field maps to a varchar column

Usage:
  python3 check_schema_entity.py --java-dir <src/main/java> [--sql <schema.sql>] [--migrations-dir <dir>]

Exit code 1 when mismatches are found (CI-friendly).
"""

import argparse
import re
import sys
from pathlib import Path


TABLE_NAME_RE = re.compile(r'@TableName\s*\(\s*"([^"]+)"')
FIELD_RE = re.compile(r'^\s*private\s+([\w<>,\[\]\.]+)\s+(\w+)\s*;')
TABLE_FIELD_ANNOTATION = re.compile(r'@TableField\s*\(\s*(?:value\s*=\s*)?"([^"]+)"')
TABLE_FIELD_EXIST_FALSE = re.compile(r'@TableField\s*\([^)]*exist\s*=\s*false')


def camel_to_snake(name: str) -> str:
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def parse_entities(java_dir: Path):
    entities = {}
    for path in java_dir.rglob("*.java"):
        text = path.read_text(errors="ignore")
        m = TABLE_NAME_RE.search(text)
        if not m:
            continue
        table = m.group(1)
        fields = []
        lines = text.splitlines()
        pending_exist_false = False
        explicit_column = None
        for i, line in enumerate(lines):
            if re.search(r'@TableField\s*\(', line):
                pending_exist_false = bool(TABLE_FIELD_EXIST_FALSE.search(line))
                em = TABLE_FIELD_ANNOTATION.search(line)
                explicit_column = em.group(1) if em else None
            fm = FIELD_RE.match(line)
            if fm:
                ftype, fname = fm.group(1), fm.group(2)
                fields.append({
                    "name": fname,
                    "type": ftype,
                    "column": explicit_column or camel_to_snake(fname),
                    "exist_false": pending_exist_false,
                })
                pending_exist_false = False
                explicit_column = None
        entities[table] = {"file": path, "fields": fields}
    return entities


def parse_sql_columns(sql_files):
    columns = {}
    for sf in sql_files:
        text = sf.read_text(errors="ignore")
        for tm in re.finditer(r'CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?\s*\((.*?)\)\s*(?:ENGINE|;|COMMENT)', text, re.S | re.I):
            table, body = tm.group(1), tm.group(2)
            cols = set()
            types = {}
            for line in body.splitlines():
                lm = re.match(r'\s*`?(\w+)`?\s+([a-z]+(?:\([^)]*\))?)', line, re.I)
                if lm and lm.group(1) not in ("PRIMARY", "KEY", "UNIQUE", "INDEX", "CONSTRAINT", "FOREIGN"):
                    col = lm.group(1)
                    cols.add(col)
                    types[col] = lm.group(2).lower()
            entry = columns.setdefault(table, {"cols": set(), "types": {}})
            entry["cols"].update(cols)
            entry["types"].update(types)
        # Flyway migrations may build idempotent ALTER statements inside SQL
        # string literals; retain those columns for offline migration-chain checks.
        for am in re.finditer(
            r"ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+`?(\w+)`?\s+([a-z]+(?:\([^)]*\))?)",
            text,
            re.I,
        ):
            table, col, col_type = am.group(1), am.group(2), am.group(3).lower()
            entry = columns.setdefault(table, {"cols": set(), "types": {}})
            entry["cols"].add(col)
            entry["types"][col] = col_type
    return columns


NUMERIC_TYPES = {"bigint", "int", "tinyint", "smallint", "mediumint", "decimal", "float", "double", "numeric"}


def check(entities, tables):
    errors, warnings, infos = [], [], []
    for table, ent in sorted(entities.items()):
        t = tables.get(table)
        if not t:
            errors.append(f"[{table}] 表在 SQL 中不存在 (entity: {ent['file'].name})")
            continue
        for f in ent["fields"]:
            if f["exist_false"]:
                continue
            col = f["column"]
            if col not in t["cols"]:
                errors.append(f"[{table}] 实体字段 {f['name']} ({f['type']}) -> 列 {col} 在表中不存在")
                continue
            col_type = t["types"].get(col, "")
            base = re.sub(r'\(.*', '', col_type)
            if f["type"] == "String" and base in NUMERIC_TYPES:
                warnings.append(f"[{table}] 实体 String 字段 {f['name']} -> 列 {col} 是 {col_type}(JSON数组/多值存储与数值列冲突)")
            elif f["type"] != "String" and "varchar" in col_type:
                warnings.append(f"[{table}] 实体 {f['type']} 字段 {f['name']} -> 列 {col} 是 {col_type}")
    for table, t in sorted(tables.items()):
        ent = entities.get(table)
        if not ent:
            infos.append(f"[{table}] SQL 中有表但无实体(未检查)")
    return errors, warnings, infos


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java-dir", required=True, type=Path)
    ap.add_argument("--sql", action="append", type=Path, default=[])
    ap.add_argument("--migrations-dir", type=Path)
    args = ap.parse_args()

    entities = parse_entities(args.java_dir)
    sql_files = list(args.sql)
    if args.migrations_dir:
        sql_files.extend(sorted(args.migrations_dir.glob("*.sql")))
    if not sql_files:
        ap.error("provide --sql and/or --migrations-dir")
    tables = parse_sql_columns(sql_files)
    errors, warnings, infos = check(entities, tables)

    print(f"实体 {len(entities)} 个, SQL 表 {len(tables)} 张")
    for tag, items in (("ERROR", errors), ("WARN", warnings), ("INFO", infos)):
        if items:
            print(f"\n{tag} ({len(items)}):")
            for i in items:
                print(f"  {i}")
    print(f"\n结论: {len(errors)} 个错误, {len(warnings)} 个警告")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
