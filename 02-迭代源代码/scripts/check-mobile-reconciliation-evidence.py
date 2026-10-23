#!/usr/bin/env python3
"""Validate sanitized PC/Mini Program same-data reconciliation evidence."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


HEADERS = [
    "验收项",
    "后端环境",
    "账号角色/权限组合",
    "PC 证据",
    "小程序证据",
    "接口/权限结果",
    "结论",
    "遗留差异",
]

REQUIRED_ITEMS = [
    "登录与权限",
    "客户与联系人/财务/指标/时间轴",
    "四项服务与附件",
    "文件中心",
    "审批/公海/提醒/统计/排行",
    "PC-only 边界",
]

ALLOWED_CONCLUSIONS = {"通过", "失败", "阻塞"}
PLACEHOLDERS = {"", "待执行", "todo", "TODO", "-", "—", "无"}
SENSITIVE_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in [
        r"\bBearer\s+[A-Za-z0-9._~+/=-]+",
        r"\btoken\s*[:=]",
        r"\bpassword\s*[:=]",
        r"\bcookie\s*[:=]",
        r"\bauthorization\s*[:=]",
        r"sk-[A-Za-z0-9]",
        r"eyJ[A-Za-z0-9_-]{10,}\.",
    ]
]


def split_markdown_row(line: str) -> list[str]:
    stripped = line.strip()
    if not stripped.startswith("|") or not stripped.endswith("|"):
        return []
    return [cell.strip() for cell in stripped.strip("|").split("|")]


def is_separator(row: list[str]) -> bool:
    return bool(row) and all(re.fullmatch(r":?-{3,}:?", cell.strip()) for cell in row)


def parse_table(text: str) -> list[dict[str, str]]:
    lines = text.splitlines()
    rows: list[dict[str, str]] = []
    header_index = -1

    for index, line in enumerate(lines):
        cells = split_markdown_row(line)
        if cells == HEADERS:
            header_index = index
            break

    if header_index < 0:
        raise ValueError("missing reconciliation table header")

    for line in lines[header_index + 1 :]:
        cells = split_markdown_row(line)
        if not cells:
            if rows:
                break
            continue
        if is_separator(cells):
            continue
        if len(cells) != len(HEADERS):
            raise ValueError(f"table row has {len(cells)} cells, expected {len(HEADERS)}: {line}")
        rows.append(dict(zip(HEADERS, cells)))

    if not rows:
        raise ValueError("reconciliation table has no data rows")
    return rows


def is_placeholder(value: str) -> bool:
    return value.strip() in PLACEHOLDERS


def validate(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    errors: list[str] = []

    for pattern in SENSITIVE_PATTERNS:
        match = pattern.search(text)
        if match:
            errors.append(f"possible sensitive value matched `{pattern.pattern}` near `{match.group(0)[:24]}`")

    try:
        rows = parse_table(text)
    except ValueError as exc:
        return errors + [str(exc)]

    by_item = {row["验收项"]: row for row in rows}
    for item in REQUIRED_ITEMS:
        if item not in by_item:
            errors.append(f"missing required reconciliation item: {item}")
            continue
        row = by_item[item]
        for field in ["后端环境", "账号角色/权限组合", "PC 证据", "小程序证据", "接口/权限结果", "结论"]:
            if is_placeholder(row[field]):
                errors.append(f"{item}: `{field}` is not filled")
        if row["结论"] not in ALLOWED_CONCLUSIONS:
            errors.append(f"{item}: `结论` must be one of {' / '.join(sorted(ALLOWED_CONCLUSIONS))}")
        if row["结论"] in {"失败", "阻塞"} and is_placeholder(row["遗留差异"]):
            errors.append(f"{item}: failed or blocked rows must describe `遗留差异`")

    return errors


def print_template() -> None:
    print("| " + " | ".join(HEADERS) + " |")
    print("| " + " | ".join(["---"] * len(HEADERS)) + " |")
    for item in REQUIRED_ITEMS:
        print(f"| {item} |  |  |  |  |  | 待执行 |  |")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", nargs="?", type=Path, help="sanitized Markdown evidence file")
    parser.add_argument("--print-template", action="store_true", help="print a blank reconciliation table")
    args = parser.parse_args()

    if args.print_template:
        print_template()
        return 0

    if args.evidence is None:
        parser.error("evidence file is required unless --print-template is used")

    errors = validate(args.evidence)
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print(f"PASS: {args.evidence} contains complete sanitized PC/Mini Program same-data reconciliation evidence.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
