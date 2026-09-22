#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""报告生成(规范§20/§21):findings.json → markdown 报告集 + SARIF 2.1.0。
证据链字段(发现者/时间/文件行/规则/状态)全部来自统一问题模型,不额外编造。"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(os.getcwd()).resolve()
if not (ROOT / ".audit" / "config" / "audit.config.yaml").is_file():
    raise SystemExit("请在项目根目录运行")
REPORTS = ROOT / ".audit" / "reports"
SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "INFO": 4}


def guarded_write(filename, text):
    target = REPORTS / filename
    if REPORTS.resolve() not in target.resolve().parents:
        raise SystemExit("非法写出路径: " + filename)
    REPORTS.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return target


def load_latest_findings():
    fdir = ROOT / ".audit" / "findings"
    cands = sorted(fdir.glob("findings-*.json")) if fdir.is_dir() else []
    if not cands:
        raise SystemExit("没有 findings;先运行 audit.py")
    return json.loads(cands[-1].read_text(encoding="utf-8")), cands[-1].name


def sarif(findings):
    results = []
    for f in findings:
        results.append({
            "ruleId": f.get("rule") or f.get("source", "unknown"),
            "level": {"CRITICAL": "error", "HIGH": "error", "MEDIUM": "warning",
                      "LOW": "note", "INFO": "note"}.get(f.get("severity"), "note"),
            "message": {"text": "[{}] {} {}".format(f.get("id"), f.get("title"), f.get("description", ""))[:500]},
            "locations": [{"physicalLocation": {
                "artifactLocation": {"uri": f.get("file", "")},
                "region": {"startLine": max(1, int(f.get("line") or 1))}}}],
            "properties": {"id": f.get("id"), "status": f.get("status"),
                           "confidence": f.get("confidence"), "detectedBy": f.get("detectedBy")},
        })
    return {"$schema": "https://json.schemastore.org/sarif-2.1.0.json",
            "version": "2.1.0", "runs": [{"tool": {"driver": {"name": "aigicrm-audit", "informationUri": "local"}},
                                          "results": results}]}


def table(findings):
    if not findings:
        return "_无_\n"
    lines = ["| ID | 严重度 | 置信 | 状态 | 来源 | 文件:行 | 标题 |", "|---|---|---|---|---|---|---|"]
    for f in findings:
        loc = "{}:{}".format(f.get("file", "?"), f.get("line") or 0)
        lines.append("| {} | {} | {} | {} | {} | `{}` | {} |".format(
            f.get("id"), f.get("severity"), f.get("confidence"), f.get("status"),
            "+".join(f.get("detectedBy") or [f.get("source", "?")]), loc, str(f.get("title"))[:80]))
    return "\n".join(lines) + "\n"


def main():
    data, src_name = load_latest_findings()
    findings = data.get("findings", [])
    tools = data.get("tools", [])
    gate = data.get("gate", {})
    meta = {k: data.get(k) for k in ("mode", "started_at", "branch", "commit", "stack_profile")}
    by_sev = {s: [f for f in findings if f.get("severity") == s] for s in SEV_ORDER}
    open_only = [f for f in findings if f.get("status") == "OPEN"]
    accepted = [f for f in findings if f.get("status") == "ACCEPTED"]
    resolved = [f for f in findings if f.get("status") == "RESOLVED"]

    now = datetime.now(timezone.utc).isoformat()
    head = "# AIGICRM 审计报告({mode})\n\n- 生成时间: {t}\n- 分支/提交: `{b}` / `{c}`\n- 数据源: {src}\n- 门禁: **{gv}**\n\n".format(
        mode=meta.get("mode"), t=now, b=meta.get("branch"), c=meta.get("commit"),
        src=src_name, gv=gate.get("verdict", "N/A"))

    guarded_write("audit-summary.md", head + "## 严重度分布\n\n" + "\n".join(
        "- **{}**: {} 条(OPEN {} / ACCEPTED {} / RESOLVED {})".format(
            s, len(by_sev[s]),
            len([f for f in by_sev[s] if f.get("status") == "OPEN"]),
            len([f for f in by_sev[s] if f.get("status") == "ACCEPTED"]),
            len([f for f in by_sev[s] if f.get("status") == "RESOLVED"]))
        for s in SEV_ORDER) + "\n\n## 工具状态\n\n" + "\n".join(
        "- `{}`: {}".format(t.get("id"), t.get("status")) for t in tools) + "\n")

    guarded_write("audit-full.md", head + "## 全部问题\n\n" + table(findings)
                  + "\n## OPEN\n\n" + table(open_only)
                  + "\n## ACCEPTED(已接受风险)\n\n" + table(accepted)
                  + "\n## RESOLVED(已修复,证据见各 finding)\n\n" + table(resolved))

    guarded_write("security-report.md", head + "## 安全类问题\n\n" + table(
        [f for f in findings if f.get("category") == "security"]))

    guarded_write("remaining-risk.md", head + "## 未处置风险(OPEN)\n\n" + table(open_only)
                  + "\n## 已接受风险(ACCEPTED,须有裁决依据)\n\n" + table(accepted))

    guarded_write("findings.sarif", json.dumps(sarif(findings), ensure_ascii=False, indent=1))
    print(json.dumps({"reports": ["audit-summary.md", "audit-full.md", "security-report.md",
                                  "remaining-risk.md", "findings.sarif"],
                      "source": src_name}, ensure_ascii=False))


if __name__ == "__main__":
    main()
