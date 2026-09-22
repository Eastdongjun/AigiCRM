#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JEV 分诊 worker:必须在 JEV venv(torch/mlx) 下运行,进程内 load+score,零命令拼接。
由 jev_triage.py 以固定字面量参数拉起;输入=项目 .audit/findings 最新清单,输出=同目录 jev-triage-*.json。"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

JEV_SCRIPTS = "/Users/dongjun/东来/Agent/01-Agents/04-JEV/scripts"
ROOT = Path(__file__).resolve().parent.parent.parent          # scripts → .audit → 项目根
FINDINGS_DIR = ROOT / ".audit" / "findings"

QUESTIONS = {
    "misreport": {"type": "noul", "question": "这个审计发现更可能是误报还是真问题?",
                  "options": ["误报", "真问题"]},
    "route": {"type": "choice", "question": "该发现的处置路由?",
              "options": ["自动修复", "排队整改", "需人工深审"]},
    "risk": {"type": "score", "question": "综合风险等级?", "options": ["低", "中", "高"]},
}


def band(conf):
    if conf > 0.7:
        return "采纳"
    if conf >= 0.5:
        return "需复核"
    return "转人工"


def main():
    sys.path.insert(0, JEV_SCRIPTS)
    from system_one_infer import load, score  # noqa: E402(JEV venv 内可用)

    cands = sorted(FINDINGS_DIR.glob("findings-*.json"))
    if not cands:
        print("no-findings", file=sys.stderr)
        return 2
    data = json.loads(cands[-1].read_text(encoding="utf-8"))

    tok, model, device = load()
    rows = []
    for f in data.get("findings", []):
        if f.get("status") != "OPEN" or str(f.get("file", "")).startswith(".audit/"):
            continue
        state = ("审计发现: {} | 严重度{} 置信{} | 规则{} | 文件{}:{} | 标题: {} | 描述: {}".format(
            f.get("source"), f.get("severity"), f.get("confidence"), f.get("rule"),
            f.get("file"), f.get("line"), f.get("title"), str(f.get("description"))[:120])).strip()
        raw = score(state, QUESTIONS, tok, model, device)
        entry = {"finding_id": f.get("id"), "title": f.get("title"), "bands": {}}
        for qid in QUESTIONS:
            node = raw.get(qid) or {}
            conf = float(node.get("conf") or 0)
            entry["bands"][qid] = {"value": node.get("top"),
                                   "conf": round(conf, 3), "band": band(conf)}
        rows.append(entry)

    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    payload = {"source_findings": cands[-1].name, "triaged_at": ts,
               "tool": "jev(system-one,in-process)", "license_note": "scorer CC-BY-NC,仅开发期advisory",
               "count": len(rows), "results": rows}
    FINDINGS_DIR.mkdir(parents=True, exist_ok=True)
    target = FINDINGS_DIR / ("jev-triage-" + ts + ".json")
    if FINDINGS_DIR.resolve() not in target.resolve().parents:
        raise SystemExit("非法写出路径")
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(target.name)


if __name__ == "__main__":
    sys.exit(main())
