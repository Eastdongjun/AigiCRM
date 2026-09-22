#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统一问题模型转换(规范§16):外部扫描器原始输出 → 统一 Finding 列表。
支持: sonar(list issues --format json)。其余扫描器由 audit.py 内建适配直接产出统一模型。"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

SEVERITY_MAP = {"BLOCKER": "CRITICAL", "CRITICAL": "CRITICAL", "MAJOR": "HIGH",
                "MINOR": "MEDIUM", "INFO": "INFO"}


def sonar_to_unified(raw, counter_start=1):
    issues = raw.get("issues") if isinstance(raw, dict) else raw
    findings = []
    for i, iss in enumerate(issues or [], start=counter_start):
        comp = iss.get("component", "")
        fpath = comp.split(":", 1)[1] if ":" in comp else comp
        sev = SEVERITY_MAP.get(str(iss.get("severity", "MAJOR")).upper(), "MEDIUM")
        findings.append({
            "id": "AUDIT-{:s}-{:05d}".format(
                datetime.now(timezone.utc).strftime("%Y%m%d"), i),
            "source": "sonarqube",
            "category": "quality" if sev in ("MEDIUM", "INFO") else "security",
            "severity": sev,
            "confidence": "HIGH",
            "language": (iss.get("language") or "unknown"),
            "framework": "spring-boot",
            "file": fpath,
            "line": iss.get("line") or 0,
            "rule": iss.get("rule", ""),
            "title": iss.get("message", "")[:120],
            "description": iss.get("message", ""),
            "evidence": {"key": iss.get("key"), "type": iss.get("type")},
            "impact": "",
            "rootCause": "",
            "recommendation": "",
            "status": "OPEN",
            "detectedBy": ["sonarqube"],
        })
    return findings


def main():
    if len(sys.argv) < 2:
        raise SystemExit("用法: normalize_results.py <sonar-issues.json> [out.json]")
    src = Path(sys.argv[1])
    raw = json.loads(src.read_text(encoding="utf-8"))
    out = sonar_to_unified(raw)
    payload = json.dumps(out, ensure_ascii=False, indent=2)
    if len(sys.argv) > 2:
        target = Path(sys.argv[2]).resolve()
        if target.parent not in target.parents and not target.parent.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(payload, encoding="utf-8")
        print("[ok] " + str(target) + " (" + str(len(out)) + " findings)", file=sys.stderr)
    else:
        print(payload)


if __name__ == "__main__":
    main()
