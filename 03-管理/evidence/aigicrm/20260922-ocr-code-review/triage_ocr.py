#!/usr/bin/env python3
"""Triage open-code-review scan JSON into a compact, reviewable table.

Usage: python3 triage_ocr.py <ocr-scan.json> [--out <dir>]
Produces:
  - ocr-findings.tsv        one row per finding (severity, category, file, line, title)
  - ocr-summary.md          counts by severity / category / directory + top files
"""
import json
import sys
import re
import collections
from pathlib import Path


def first_line(text, limit=140):
    if not text:
        return ""
    line = text.strip().splitlines()[0].strip()
    line = re.sub(r"^[#*\-\s]+", "", line)
    line = re.sub(r"\s+", " ", line)
    return line[:limit]


def main():
    src = Path(sys.argv[1])
    outdir = Path(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[2] == "--out" else src.parent
    data = json.loads(src.read_text(encoding="utf-8"))

    comments = data.get("comments") or []
    items = data.get("items") or []
    if not comments and items:
        for it in items:
            for c in (it.get("comments") or []):
                c.setdefault("path", it.get("path"))
                comments.append(c)

    rows = []
    for c in comments:
        rows.append({
            "severity": (c.get("severity") or "unknown").lower(),
            "category": (c.get("category") or "uncategorized").lower(),
            "path": c.get("path") or "",
            "start": c.get("start_line") or 0,
            "end": c.get("end_line") or 0,
            "title": first_line(c.get("content")),
            "content": c.get("content") or "",
            "suggestion": c.get("suggestion_code") or "",
            "existing": c.get("existing_code") or "",
        })

    sev_rank = {"critical": 0, "blocker": 0, "high": 1, "major": 1, "medium": 2, "minor": 3, "low": 3, "info": 4, "unknown": 5}
    rows.sort(key=lambda r: (sev_rank.get(r["severity"], 9), r["path"], r["start"]))

    tsv = outdir / "ocr-findings.tsv"
    with tsv.open("w", encoding="utf-8") as f:
        f.write("severity\tcategory\tpath\tstart\tend\ttitle\n")
        for r in rows:
            f.write("\t".join(str(r[k]).replace("\t", " ").replace("\n", " ")
                              for k in ("severity", "category", "path", "start", "end", "title")) + "\n")

    sev = collections.Counter(r["severity"] for r in rows)
    cat = collections.Counter(r["category"] for r in rows)
    byfile = collections.Counter(r["path"] for r in rows)
    bydir = collections.Counter("/".join(r["path"].split("/")[:4]) for r in rows)

    summary = data.get("summary") or {}
    md = []
    md.append("# ocr scan triage\n")
    md.append(f"- source: `{src}`")
    md.append(f"- findings: **{len(rows)}**")
    md.append(f"- files_reviewed: {summary.get('files_reviewed')}")
    md.append(f"- tokens: total={summary.get('total_tokens')} in={summary.get('input_tokens')} out={summary.get('output_tokens')}")
    md.append(f"- elapsed: {summary.get('elapsed')}")
    md.append("\n## by severity\n")
    for k, v in sorted(sev.items(), key=lambda kv: sev_rank.get(kv[0], 9)):
        md.append(f"- {k}: {v}")
    md.append("\n## by category\n")
    for k, v in cat.most_common():
        md.append(f"- {k}: {v}")
    md.append("\n## top files\n")
    for k, v in byfile.most_common(25):
        md.append(f"- {v}\t{k}")
    md.append("\n## by directory\n")
    for k, v in bydir.most_common(25):
        md.append(f"- {v}\t{k}")
    (outdir / "ocr-summary.md").write_text("\n".join(md) + "\n", encoding="utf-8")

    print(f"findings={len(rows)} -> {tsv}")
    print("severity:", dict(sev))
    print("category:", dict(cat))


if __name__ == "__main__":
    main()
