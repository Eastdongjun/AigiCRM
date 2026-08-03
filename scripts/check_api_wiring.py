#!/usr/bin/env python3
"""Cross-check frontend axios API calls against backend Spring controller mappings.

Catches the bug class:
  - frontend calls a path that no backend controller implements (404 at runtime)
  - backend endpoint exists but frontend never calls it (orphan, informational)

Usage:
  python3 check_api_wiring.py --frontend-api <ycmn-ui/src/api> --backend <src/main/java>

Exit code 1 when frontend paths are unmatched (CI-friendly).
"""

import argparse
import re
import sys
from pathlib import Path


def parse_backend(java_dir: Path):
    """Return set of (METHOD, path_regex) and list of human-readable endpoints."""
    endpoints = []
    readable = []
    class_re = re.compile(r'@RequestMapping\s*\(\s*"([^"]+)"')
    method_re = re.compile(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(?:\s*\(\s*"([^"]+)"\s*\))?')
    for path in java_dir.rglob("*.java"):
        text = path.read_text(errors="ignore")
        base = None
        cm = class_re.search(text)
        if cm:
            base = cm.group(1).rstrip("/")
        for mm in method_re.finditer(text):
            verb = mm.group(1).replace("Mapping", "").upper()
            sub = mm.group(2) or ""
            full = (base or "") + ("/" + sub.lstrip("/") if sub else "")
            pattern = "^" + "".join(
                "[^/]+" if part.startswith("{") else re.escape(part)
                for part in re.split(r"(\{[^}]+\})", full)
            ) + "$"
            endpoints.append((verb, re.compile(pattern), full))
            readable.append(f"{verb} {full}")
    return endpoints, readable


def parse_frontend(api_dir: Path):
    """Return list of (file, verb, raw_path) for request.get/post/put/delete/... calls."""
    calls = []
    call_re = re.compile(
        r"request\.(get|post|put|delete|patch)\(\s*(?:`([^`]*)`|'([^']*)'|\"([^\"]*)\")",
        re.S,
    )
    for path in api_dir.rglob("*.js"):
        text = path.read_text(errors="ignore")
        for m in call_re.finditer(text):
            calls.append((path.name, m.group(1).upper(), m.group(2) or m.group(3) or m.group(4)))
    return calls


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--frontend-api", required=True, type=Path)
    ap.add_argument("--backend", required=True, type=Path)
    args = ap.parse_args()

    endpoints, readable = parse_backend(args.backend)
    calls = parse_frontend(args.frontend_api)
    unmatched, matched = [], []

    for fname, verb, raw in calls:
        # substitute template variables with a concrete segment, then regex-match
        test_path = re.sub(r"\$\{[^}]+\}", "p", raw)
        candidates = [test_path]
        if not test_path.startswith("/api"):
            candidates.append("/api" + test_path)
        hit = any(
            v == verb and any(preg.fullmatch(c) for c in candidates)
            for v, preg, _ in endpoints
        )
        if hit:
            matched.append(f"{verb} {raw} ({fname})")
        else:
            unmatched.append(f"{verb} {raw} ({fname})")

    print(f"前端调用 {len(calls)} 条, 后端端点 {len(readable)} 个")
    if unmatched:
        print(f"\nERROR - 前端调用但后端无对应端点 ({len(unmatched)}):")
        for u in sorted(unmatched):
            print(f"  {u}")
    if matched:
        print(f"\nOK ({len(matched)} 条已匹配)")
    print(f"\n后端全部端点:")
    for r in sorted(readable):
        print(f"  {r}")
    sys.exit(1 if unmatched else 0)


if __name__ == "__main__":
    main()
