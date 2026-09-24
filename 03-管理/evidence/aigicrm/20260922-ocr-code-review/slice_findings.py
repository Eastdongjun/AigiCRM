#!/usr/bin/env python3
"""Slice ocr findings TSV into per-area TSV files for parallel verification.

Usage: python3 slice_findings.py <findings.tsv> <outdir>
Areas: core, module-customer, module-system, module-biz, ui, miniprogram, other
"""
import csv
import sys
import os
from pathlib import Path

AREAS = {
    "core": ["/com/aigicrm/common/", "/com/aigicrm/security/", "/com/aigicrm/config/"],
    "module-customer": ["/com/aigicrm/module/customer/", "/com/aigicrm/module/file/",
                        "/com/aigicrm/module/approval/", "/com/aigicrm/module/sea/",
                        "/com/aigicrm/module/reminder/"],
    "module-system": ["/com/aigicrm/module/system/", "/com/aigicrm/module/auth/"],
    "module-biz": ["/com/aigicrm/module/incentive/", "/com/aigicrm/module/score/",
                   "/com/aigicrm/module/task/", "/com/aigicrm/module/statistics/",
                   "/com/aigicrm/task/"],
    "ui": ["/aigicrm-ui/src/"],
    "miniprogram": ["/aigicrm-miniprogram/"],
}


def area_of(path):
    for name, keys in AREAS.items():
        if any(k in path for k in keys):
            return name
    return "other"


def main():
    src = Path(sys.argv[1])
    outdir = Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)
    buckets = {k: [] for k in list(AREAS) + ["other"]}
    with src.open(encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            buckets[area_of(row["path"])].append(row)
    header = "severity\tcategory\tpath\tstart\tend\ttitle\n"
    for name, rows in buckets.items():
        p = outdir / f"findings-{name}.tsv"
        with p.open("w", encoding="utf-8") as f:
            f.write(header)
            for r in rows:
                f.write("\t".join(str(r[k]).replace("\t", " ").replace("\n", " ")
                                  for k in ("severity", "category", "path", "start", "end", "title")) + "\n")
        if rows:
            print(f"{name}: {len(rows)} -> {p}")


if __name__ == "__main__":
    main()
