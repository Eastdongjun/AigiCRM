#!/usr/bin/env python3
"""Slice ocr findings JSON (with full comment text) into per-area JSON files.

Usage: python3 slice_findings_json.py <findings.json> <outdir>
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from slice_findings import area_of, AREAS  # noqa: E402


def main():
    src = Path(sys.argv[1])
    outdir = Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)
    data = json.loads(src.read_text(encoding="utf-8"))
    buckets = {k: [] for k in list(AREAS) + ["other"]}
    for c in data.get("comments") or []:
        buckets[area_of(c.get("path") or "")].append({
            "severity": (c.get("severity") or "unknown"),
            "category": (c.get("category") or "uncategorized"),
            "path": c.get("path"),
            "start_line": c.get("start_line"),
            "end_line": c.get("end_line"),
            "content": c.get("content"),
            "existing_code": c.get("existing_code"),
            "suggestion_code": c.get("suggestion_code"),
        })
    for name, rows in buckets.items():
        p = outdir / f"findings-{name}.json"
        p.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
        if rows:
            print(f"{name}: {len(rows)} -> {p}")


if __name__ == "__main__":
    main()
