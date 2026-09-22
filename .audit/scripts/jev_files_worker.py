#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JEV 文件用途判定 worker(JEV venv 下运行,进程内 load+score)。
输入=项目 .audit/evidence/jev-files-input.json(父脚本生成),
输出=同目录 jev-files-<ts>.json。仅判定,不删除任何文件。"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

JEV_SCRIPTS = "/Users/dongjun/东来/Agent/01-Agents/04-JEV/scripts"
ROOT = Path(__file__).resolve().parent.parent.parent
EVID = ROOT / ".audit" / "evidence"
INPUT = EVID / "jev-files-input.json"

QUESTIONS = {
    "cls": {"type": "choice", "question": "该项目根目录条目的用途判定?",
            "options": ["核心必需", "有用保留", "疑似无用", "明显无用"]},
    "del_safe": {"type": "noul", "question": "该条目现在删除是否安全?",
                 "options": ["可安全删除", "不可删除"]},
}


def band(conf):
    if conf > 0.7:
        return "采纳"
    if conf >= 0.5:
        return "需复核"
    return "转人工"


def main():
    if not INPUT.is_file():
        print("no-input", file=sys.stderr)
        return 2
    entries = json.loads(INPUT.read_text(encoding="utf-8"))
    sys.path.insert(0, JEV_SCRIPTS)
    from system_one_infer import load, score  # noqa: E402

    tok, model, device = load()
    rows = []
    for e in entries:
        if e.get("rule"):                      # 硬规则条目不进模型
            rows.append({"entry": e["name"], "hard_rule": e["rule"],
                         "bands": {"cls": {"value": e["rule"], "conf": 1.0, "band": "硬规则"},
                                   "del_safe": {"value": e.get("del", "不可删除"), "conf": 1.0, "band": "硬规则"}},
                         "meta": e.get("meta", "")})
            continue
        state = ("项目根目录条目: {} | 类型{} | 大小{} | 修改于{} | {} | 项目=AIGICRM企业CRM(Java后端+Vue前端+微信小程序)".format(
            e["name"], e.get("typ"), e.get("size"), e.get("mtime"), e.get("tracked"))).strip()
        raw = score(state, QUESTIONS, tok, model, device)
        entry = {"entry": e["name"], "meta": e.get("meta", ""),
                 "bands": {qid: {"value": (raw.get(qid) or {}).get("top"),
                                 "conf": round(float((raw.get(qid) or {}).get("conf") or 0), 3),
                                 "band": band(float((raw.get(qid) or {}).get("conf") or 0))}
                           for qid in QUESTIONS}}
        rows.append(entry)

    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    payload = {"judged_at": ts, "tool": "jev(system-one,in-process)",
               "license_note": "scorer CC-BY-NC,仅开发期advisory",
               "count": len(rows), "results": rows}
    EVID.mkdir(parents=True, exist_ok=True)
    target = EVID / ("jev-files-" + ts + ".json")
    if EVID.resolve() not in target.resolve().parents:
        raise SystemExit("非法写出路径")
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(target.name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
