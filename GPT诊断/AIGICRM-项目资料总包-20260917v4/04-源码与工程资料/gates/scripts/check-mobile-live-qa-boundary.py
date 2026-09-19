#!/usr/bin/env python3
"""Ensure remaining mobile/PC open items are clearly classified."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TODO = ROOT / "docs" / "mobile-pc-unified-todolist.md"

OPEN_PREFIX = "- [ ] "
MANUAL_HEADING_MARKERS = ("Manual QA Checklist",)
EXEMPT_HEADINGS = {
    "P7 Account Binding Evolution",
    "P8 Quality And Release Gates",
}


def heading_name(line: str) -> str:
    return line.lstrip("#").strip()


def main() -> int:
    section = ""
    failures: list[str] = []

    for line_number, line in enumerate(TODO.read_text(encoding="utf-8").splitlines(), start=1):
        if line.startswith("## "):
            section = heading_name(line)
            continue
        if not line.startswith(OPEN_PREFIX):
            continue

        text = line[len(OPEN_PREFIX) :]
        if section in EXEMPT_HEADINGS:
            continue
        if any(marker in section for marker in MANUAL_HEADING_MARKERS):
            continue
        if text.startswith("Live QA only: "):
            continue
        failures.append(
            f"{TODO.relative_to(ROOT)}:{line_number}: unchecked implementation item must be moved to a manual/release section or start with `Live QA only:`"
        )

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print("PASS: mobile/PC open items distinguish implementation gaps from live QA, optional binding, and release gates.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
