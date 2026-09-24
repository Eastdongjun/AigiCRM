#!/usr/bin/env python3
"""Compute findings not yet covered by already-verified slice files.

Usage: python3 delta_findings.py <all_findings.json> <out.json> <verified_slice.json> [...]
"""
import json
import sys
from pathlib import Path


def key(c):
    return (c.get("path"), c.get("start_line"), c.get("end_line"), (c.get("content") or "")[:40])


def main():
    allp = Path(sys.argv[1])
    outp = Path(sys.argv[2])
    verified = set()
    for p in sys.argv[3:]:
        for c in json.loads(Path(p).read_text(encoding="utf-8")):
            verified.add(key(c))
    data = json.loads(allp.read_text(encoding="utf-8"))
    rows = [c for c in (data.get("comments") or []) if key(c) not in verified]
    outp.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"all={(len(data.get('comments') or []))} verified_keys={len(verified)} remaining={len(rows)} -> {outp}")


if __name__ == "__main__":
    main()
