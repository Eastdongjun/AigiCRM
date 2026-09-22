#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JEV 分诊入口:以固定命令拉起 JEV venv 下的 worker(进程内 load+score),
零动态命令拼接;worker 产物=jev-triage-<ts>.json,本脚本只汇总打印。
置信度分档:>0.7 采纳;0.5~0.7 需复核;<0.5 转人工(JEV AGENTS.md 规则)。"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
VENV_PY = "/Users/dongjun/东来/Agent/01-Agents/06-JEV/venv/bin/python"   # 固定字面量命令位
WORKER = str(ROOT / ".audit" / "scripts" / "jev_triage_worker.py")
FINDINGS_DIR = ROOT / ".audit" / "findings"


def main():
    if not Path(VENV_PY).exists():
        raise SystemExit("TOOL_NOT_INSTALLED: 缺 JEV venv python(" + VENV_PY + ")")
    if not Path(WORKER).is_file():
        raise SystemExit("worker 缺失: " + WORKER)
    out = subprocess.run(["/Users/dongjun/东来/Agent/01-Agents/06-JEV/venv/bin/python", WORKER],
                         capture_output=True, text=True, timeout=900)
    if out.returncode != 0:
        sys.stderr.write((out.stderr or "")[-400:])
        raise SystemExit("JEV worker 失败(exit={})".format(out.returncode))
    name = out.stdout.strip().splitlines()[-1]
    target = FINDINGS_DIR / name
    payload = json.loads(target.read_text(encoding="utf-8"))
    print(json.dumps({"triaged": payload["count"], "source": payload["source_findings"],
                      "output": name}, ensure_ascii=False))
    for r in payload["results"]:
        b = r["bands"]
        print("  {} | 误报判:{}({}) | 路由:{}({}) | 风险:{}({}) | {}".format(
            r["finding_id"],
            b["misreport"]["value"], b["misreport"]["band"],
            b["route"]["value"], b["route"]["band"],
            b["risk"]["value"], b["risk"]["band"],
            str(r["title"])[:44]))


if __name__ == "__main__":
    main()
