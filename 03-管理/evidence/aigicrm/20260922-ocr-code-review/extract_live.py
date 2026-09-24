#!/usr/bin/env python3
"""Extract findings from a live ocr session jsonl into triage-compatible JSON.

Usage: python3 extract_live.py <session.jsonl> <out.json>
"""
import json
import sys


def main():
    src, dst = sys.argv[1], sys.argv[2]
    comments = []
    done_files = []
    for line in open(src, encoding="utf-8"):
        try:
            d = json.loads(line)
        except Exception:
            continue
        if d.get("type") != "review_item_done":
            continue
        path = d.get("filePath") or d.get("newPath")
        done_files.append(path)
        for c in d.get("comments") or []:
            c.setdefault("path", path)
            comments.append(c)
    json.dump({"comments": comments,
               "summary": {"files_reviewed": len(done_files), "comments": len(comments)}},
              open(dst, "w", encoding="utf-8"), ensure_ascii=False)
    print(f"files_done={len(done_files)} comments={len(comments)} -> {dst}")


if __name__ == "__main__":
    main()
