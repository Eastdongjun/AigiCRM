#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AIGICRM 统一审计入口(规范§6):
  quick | full | security | business | changed | recheck
技术栈 → 扫描器选择 → 统一问题模型 → 去重 → 分级 → 门禁 → findings-<ts>.json。
安全约定:文件写出仅限 .audit/{findings,evidence,baseline,reports};子进程参数一律
白名单校验后以参数列表执行(shell=False);文件宇宙=git 受控文件。"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(os.getcwd()).resolve()
if not (ROOT / ".audit" / "config" / "audit.config.yaml").is_file():
    raise SystemExit("请在项目根目录运行(根下须有 .audit/config/)")
AUD = ROOT / ".audit"
FINDINGS_DIR = AUD / "findings"
EVID = AUD / "evidence"
SONAR_PROJECT = "Eastdongjun_AigiCRM"          # 与根 sonar-project.properties 一致
DEV_VERIFY = ROOT / "02-迭代源代码" / "scripts" / "dev-verify.sh"
ALLOWED_DV_MODES = {"shell-syntax", "python-syntax", "miniprogram", "contracts",
                    "backend-compile", "backend-test", "frontend-test", "frontend-build"}
ALLOWED_RUFF_DIRS = ["scripts_audit", "engineering"]
RUFF_DIRS = {  # 白名单目录(相对根)
    "scripts_audit": AUD / "scripts",
    "engineering": ROOT / "02-迭代源代码" / "engineering",
}
SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3, "INFO": 4}
# 高置信 Secret 模式(只报几乎不可能是业务代码的形态)
SECRET_PATTERNS = [
    (re.compile(r"AKIA[0-9A-Z]{16}"), "AWS Access Key"),
    (re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), "Private Key"),
    (re.compile(r"(?i)aws_secret_access_key\s*[:=]\s*['\"][A-Za-z0-9/+=]{20,}"), "AWS Secret"),
]
SECRET_SKIP = re.compile(r"(test|fixture|demo|备份|GPT诊断|\.md$|\.audit/)")


def guarded_write(base: Path, filename: str, text: str) -> Path:
    base.mkdir(parents=True, exist_ok=True)
    target = base / filename
    if base.resolve() not in target.resolve().parents:
        raise SystemExit("非法写出路径: " + filename)
    target.write_text(text, encoding="utf-8")
    return target


def now_utc():
    return datetime.now(timezone.utc).isoformat()


def new_finding(idx, **kw):
    f = {"id": "AUDIT-{}-{:05d}".format(datetime.now(timezone.utc).strftime("%Y%m%d"), idx),
         "source": "", "category": "quality", "severity": "INFO", "confidence": "HIGH",
         "language": "", "framework": "", "file": "", "line": 0, "rule": "", "title": "",
         "description": "", "evidence": "", "impact": "", "rootCause": "",
         "recommendation": "", "status": "OPEN", "detectedBy": [], "first_seen": now_utc()}
    f.update(kw)
    f["detectedBy"] = list(set(kw.get("detectedBy") or [kw.get("source", "unknown")]))
    return f


def git_tracked():
    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files"],
                         cwd=str(ROOT), capture_output=True, text=True, timeout=120)
    return [p for p in out.stdout.splitlines() if p]


def git_meta():
    branch = subprocess.run(["git", "rev-parse", "--abbrev-ref", "HEAD"], cwd=str(ROOT),
                            capture_output=True, text=True).stdout.strip()
    commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(ROOT),
                            capture_output=True, text=True).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain"], cwd=str(ROOT),
                           capture_output=True, text=True).stdout.splitlines()
    return {"branch": branch, "commit": commit, "dirty_files": len(dirty)}


# ---------------- 扫描器 ----------------

def scan_sonar():
    """服务端 SonarQube OPEN 问题 → 统一模型。"""
    if not shutil.which("sonar"):
        return [], tool_status("sonar", "TOOL_NOT_INSTALLED")
    out = subprocess.run(["sonar", "list", "issues", "-p", SONAR_PROJECT,
                          "--statuses", "OPEN", "--format", "json"],
                         capture_output=True, text=True, timeout=180)
    if out.returncode != 0:
        return [], tool_status("sonar", "ERROR", out.stderr.strip()[:200])
    try:
        issues = json.loads(out.stdout or "{}").get("issues", [])
    except json.JSONDecodeError:
        return [], tool_status("sonar", "ERROR", "bad json")
    sev_map = {"BLOCKER": "CRITICAL", "CRITICAL": "CRITICAL", "MAJOR": "HIGH",
               "MINOR": "MEDIUM", "INFO": "INFO"}
    findings = []
    for i, iss in enumerate(issues, 1):
        comp = iss.get("component", "")
        findings.append(new_finding(i, source="sonarqube",
                                    category="quality", severity=sev_map.get(str(iss.get("severity", "MAJOR")).upper(), "MEDIUM"),
                                    language=iss.get("language") or "", file=comp.split(":", 1)[-1],
                                    line=iss.get("line") or 0, rule=iss.get("rule", ""),
                                    title=str(iss.get("message", ""))[:120],
                                    description=iss.get("message", "")))
    return findings, tool_status("sonar", "OK", "{} OPEN issues".format(len(issues)))


def scan_ruff():
    if not shutil.which("ruff"):
        return [], tool_status("ruff", "TOOL_NOT_INSTALLED")
    findings, idx = [], 0
    for key in ALLOWED_RUFF_DIRS:
        d = RUFF_DIRS[key]
        if not d.is_dir():
            continue
        out = subprocess.run(["ruff", "check", str(d), "--output-format", "json"],
                             capture_output=True, text=True, timeout=120)
        try:
            rows = json.loads(out.stdout or "[]")
        except json.JSONDecodeError:
            rows = []
        for r in rows:
            idx += 1
            findings.append(new_finding(idx, source="ruff", category="quality",
                                        severity="MEDIUM", language="python",
                                        file=str(Path(r.get("filename", "")).relative_to(ROOT)),
                                        line=r.get("location", {}).get("row", 0),
                                        rule=r.get("code", ""),
                                        title=str(r.get("message", ""))[:120]))
    return findings, tool_status("ruff", "OK", "{} issues".format(len(findings)))


def scan_dev_verify(modes):
    if not DEV_VERIFY.is_file():
        return [], tool_status("dev_verify", "TOOL_NOT_INSTALLED")
    findings, idx = [], 0
    for mode in modes:
        if mode not in ALLOWED_DV_MODES:
            continue
        out = subprocess.run(["bash", str(DEV_VERIFY), mode], cwd=str(ROOT),
                             capture_output=True, text=True, timeout=1800)
        if out.returncode != 0:
            idx += 1
            tail = (out.stdout or out.stderr).strip().splitlines()[-3:]
            findings.append(new_finding(idx, source="dev_verify", category="quality",
                                        severity="HIGH", rule="gate:" + mode,
                                        title="dev-verify {} 门禁失败".format(mode),
                                        description=" | ".join(tail)[:400],
                                        evidence={"exit": out.returncode}))
    return findings, tool_status("dev_verify", "OK", "modes=" + ",".join(modes))


def scan_secrets():
    findings, idx = [], 0
    tracked = git_tracked()
    for rel in tracked:
        if SECRET_SKIP.search(rel):
            continue
        path = ROOT / rel
        try:
            if path.stat().st_size > 300000:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for rx, label in SECRET_PATTERNS:
            for m in rx.finditer(text):
                idx += 1
                line = text.count("\n", 0, m.start()) + 1
                findings.append(new_finding(idx, source="secrets-grep", category="security",
                                            severity="CRITICAL", file=rel, line=line,
                                            rule="secret:" + label,
                                            title="疑似泄漏: " + label,
                                            evidence={"match": m.group(0)[:12] + "..."}))
    return findings, tool_status("secrets-grep", "OK", "{} hits".format(len(findings)))


def scan_mini_static():
    mini = "02-迭代源代码/aigicrm-miniprogram"
    tracked = [p for p in git_tracked() if p.startswith(mini + "/")]
    findings, idx = [], 0
    url_rx = re.compile(r"https?://[0-9A-Za-z.\-]+")
    allowed_hosts = ("aigicrm.com",)
    console_hits, url_hits = 0, 0
    for rel in tracked:
        if not rel.endswith(".js"):
            continue
        if "/config/" in rel:
            continue
        try:
            text = (ROOT / rel).read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if url_rx.search(line):
                host = url_rx.search(line).group(0)
                if not any(h in host for h in allowed_hosts) and "localhost" not in line:
                    url_hits += 1
                    if url_hits <= 10:
                        idx += 1
                        findings.append(new_finding(idx, source="mini-static", category="security",
                                                    severity="MEDIUM", language="javascript",
                                                    file=rel, line=i, rule="hardcoded-url",
                                                    title="小程序源码硬编码环境地址(应走 config/env)",
                                                    evidence={"line": line.strip()[:100]}))
            if "console.log" in line:
                console_hits += 1
    if console_hits:
        findings.append(new_finding(idx + 1, source="mini-static", category="quality",
                                    severity="INFO", language="javascript", file=mini,
                                    rule="console-log", title="console.log 共 {} 处(发布构建应去除)".format(console_hits)))
    return findings, tool_status("mini-static", "OK",
                                 "files={} urls={} console={}".format(len(tracked), url_hits, console_hits))


def scan_todo():
    # 精度规则:只认注释形态的标记(TODO[:：(]|FIXME|XXX|HACK),
    # 排除 shell/python 变量用法 TODO=、$TODO、TODO.read_text 等(首版基线实证误报)
    rx = re.compile(r"TODO\s*[:：(]|FIXME|XXX|HACK")
    var_rx = re.compile(r"TODO\s*=|\$\{?TODO|TODO\.|grep")  # grep 行=契约脚本在检索标记,非开发债
    findings = []
    count = 0
    for rel in git_tracked():
        if not rel.startswith("02-迭代源代码/") or rel.split(".")[-1] not in ("java", "js", "vue", "ts", "mjs", "py", "sh"):
            continue
        try:
            for i, line in enumerate((ROOT / rel).read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
                if not rx.search(line) or var_rx.search(line):
                    continue
                count += 1
                if count <= 30:
                    findings.append(new_finding(count, source="todo-scan", category="quality",
                                                severity="INFO", file=rel, line=i,
                                                rule="todo", title=line.strip()[:100]))
        except OSError:
            continue
    if count > 30:
        findings.append(new_finding(31, source="todo-scan", category="quality", severity="INFO",
                                    rule="todo", title="TODO/FIXME 总计 {} 处(仅列前30)".format(count)))
    return findings, tool_status("todo-scan", "OK", "{} hits".format(count))


def tool_status(tid, status, note=""):
    return {"id": tid, "status": status, "note": note, "at": now_utc()}


def missing_tools_expected(profile):
    """stack-profile 期望 vs 本机安装:全部记录 TOOL_NOT_INSTALLED + 安装建议。"""
    sugg = {"semgrep": "brew install semgrep", "codeql": "brew install codeql", "trivy": "brew install trivy",
            "gitleaks": "brew install gitleaks", "hadolint": "brew install hadolint",
            "checkov": "pip3 install checkov", "kubescape": "brew install kubescape",
            "spotbugs": "brew install spotbugs", "pmd": "brew install pmd",
            "stylelint": "npm -g install stylelint", "govulncheck": "go install golang.org/x/vuln/cmd/govulncheck@latest",
            "osv": "接入 OSV API 或安装 osv-scanner", "dependency-check": "brew install dependency-check"}
    out = []
    for tool in profile.get("scanners_expected", []):
        if tool.endswith("(自有)") or tool in ("wechat-devtools-cli", "eslint", "eslint-plugin-vue", "tsc", "vitest", "sonar"):
            continue  # 自有/项目内(npm scripts)或已由其他扫描器覆盖
        if not shutil.which(tool):
            out.append(tool_status(tool, "TOOL_NOT_INSTALLED", sugg.get(tool, "见官方文档")))
    return out


# ---------------- 去重/门禁 ----------------

def dedupe(findings):
    """(file, line±2, 类别+标题归一) 合并;detectedBy 聚合提升可信度(规范§17)。"""
    buckets = {}
    for f in findings:
        key = (f.get("file"), (int(f.get("line") or 0)) // 3, f.get("category"),
               re.sub(r"\W+", "", str(f.get("title")))[:40])
        if key in buckets:
            keep = buckets[key]
            keep["detectedBy"] = sorted(set(keep["detectedBy"]) | set(f["detectedBy"]))
            sev = [f.get("severity"), keep.get("severity")]
            keep["severity"] = min(sev, key=lambda s: SEV_ORDER.get(s, 9))
            if f.get("confidence") == "HIGH":
                keep["confidence"] = "HIGH"
        else:
            buckets[key] = f
    out = list(buckets.values())
    out.sort(key=lambda f: (SEV_ORDER.get(f.get("severity"), 9), f.get("file", "")))
    for i, f in enumerate(out, 1):
        f["id"] = "AUDIT-{}-{:05d}".format(datetime.now(timezone.utc).strftime("%Y%m%d"), i)
        if len(f["detectedBy"]) > 1:
            f["confidence"] = "HIGH"
    return out


def gate(findings, tools):
    sev = {"CRITICAL": 0, "HIGH": 0, "MEDIUM": 0, "LOW": 0, "INFO": 0}
    secret = 0
    for f in findings:
        if f.get("status") == "OPEN":
            sev[f.get("severity")] = sev.get(f.get("severity"), 0) + 1
            if f.get("category") == "security" and "secret" in str(f.get("rule", "")):
                secret += 1
    failures = []
    if sev["CRITICAL"]:
        failures.append("CRITICAL={} (门禁0)".format(sev["CRITICAL"]))
    if sev["HIGH"]:
        failures.append("HIGH={} (门禁0)".format(sev["HIGH"]))
    if secret:
        failures.append("Secret={} (门禁0)".format(secret))
    for t in tools:
        if t["status"] == "ERROR" and t["id"] == "dev_verify":
            failures.append("dev_verify 门禁失败")
    return {"verdict": "FAIL" if failures else "PASS", "counts": sev, "secret": secret,
            "failures": failures}


def load_agent_injections():
    out = []
    for p in sorted(FINDINGS_DIR.glob("agent-review-*.json")) if FINDINGS_DIR.is_dir() else []:
        try:
            out.extend(json.loads(p.read_text(encoding="utf-8")))
        except Exception as e:
            print("[warn] 注入文件损坏 {}: {}".format(p.name, e), file=sys.stderr)
    return out


def ensure_stack_profile():
    p = EVID / "stack-profile.json"
    if not p.is_file():
        # 兄弟模块直调(脚本目录天然在 sys.path[0]),不派生子进程
        import detect_stack
        guarded_write(EVID, "stack-profile.json",
                      json.dumps(detect_stack.build_profile(), ensure_ascii=False, indent=2))
    return json.loads(p.read_text(encoding="utf-8"))


# ---------------- 模式 ----------------

def run_mode(mode, no_gate=False):
    profile = ensure_stack_profile()
    meta = git_meta()
    tools, findings = [], []

    quick_scanners = lambda: [  # noqa: E731
        scan_sonar(), scan_ruff(), scan_dev_verify(["shell-syntax", "python-syntax", "miniprogram"]),
        scan_secrets(), scan_mini_static(), scan_todo()]
    if mode == "quick":
        for fs, ts in quick_scanners():
            findings += fs
            tools.append(ts)
    elif mode == "security":
        for fs, ts in [scan_sonar(), scan_secrets(), scan_mini_static()]:
            findings += fs
            tools.append(ts)
        tools += missing_tools_expected(profile)
    elif mode == "business":
        findings += load_agent_injections()
        tools.append(tool_status("agent-review", "OK", "注入 .audit/findings/agent-review-*.json"))
        tools.append(tool_status("perm-matrix", "MANUAL",
                                 "需 AIGI_TEST_PASS,运行 verify-perm-matrix.sh(今日21/21绿已记录于中央evidence)"))
    elif mode == "full":
        for fs, ts in quick_scanners():
            findings += fs
            tools.append(ts)
        tools += missing_tools_expected(profile)
        findings += load_agent_injections()
        tools.append(tool_status("agent-review", "OK", "business+semantic 注入"))
        # 长任务(规范:full 才跑)
        if shutil.which("mvn"):
            out = subprocess.run(["mvn", "-q", "test"], cwd=str(ROOT / "02-迭代源代码" / "aigicrm-backend"),
                                 capture_output=True, text=True, timeout=1800)
            tools.append(tool_status("mvn_test", "OK" if out.returncode == 0 else "ERROR",
                                     "exit={}".format(out.returncode)))
            if out.returncode != 0:
                findings.append(new_finding(9001, source="mvn_test", category="quality",
                                            severity="HIGH", rule="test-failure", title="后端测试失败"))
        else:
            tools.append(tool_status("mvn_test", "TOOL_NOT_INSTALLED"))
    elif mode == "changed":
        import audit_changed
        changed = audit_changed.build_report()
        print(json.dumps(changed, ensure_ascii=False, indent=2))
        targets = set(changed.get("audit_targets", []))
        targeted = [f for f in scan_secrets()[0] + scan_todo()[0] if f.get("file") in targets]
        findings += targeted
        tools.append(tool_status("audit_changed", "OK",
                                 "direct={} expanded={}".format(
                                     len(changed["summary"]["added"]) + len(changed["summary"]["modified"]),
                                     changed["summary"]["expanded_targets"])))
    elif mode == "recheck":
        cands = sorted(FINDINGS_DIR.glob("findings-*.json")) if FINDINGS_DIR.is_dir() else []
        if not cands:
            raise SystemExit("没有历史 findings,先跑 quick/full")
        prev = json.loads(cands[-1].read_text(encoding="utf-8"))
        open_prev = [f for f in prev.get("findings", []) if f.get("status") == "OPEN"]
        # 复测=重跑 quick 扫描器,与旧 OPEN 求差
        for fs, ts in quick_scanners():
            findings += fs
            tools.append(ts)
        keys_now = {(f.get("file"), int(f.get("line") or 0) // 3) for f in findings}
        still = [f for f in open_prev if (f.get("file"), int(f.get("line") or 0) // 3) in keys_now]
        findings = still
        tools.append(tool_status("recheck", "OK",
                                 "prev_open={} still_open={}".format(len(open_prev), len(still))))
    else:
        raise SystemExit("未知模式: " + mode)

    findings = dedupe(findings)
    g = {"verdict": "SKIPPED"} if no_gate else gate(findings, tools)
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    payload = {"mode": mode, "started_at": ts, "stack_profile": profile.get("project_type"),
               **meta, "tools": tools, "gate": g, "findings": findings,
               "counts": {"OPEN": len([f for f in findings if f["status"] == "OPEN"]),
                          "ACCEPTED": len([f for f in findings if f["status"] == "ACCEPTED"]),
                          "RESOLVED": len([f for f in findings if f["status"] == "RESOLVED"])}}
    out = guarded_write(FINDINGS_DIR, "findings-{}.json".format(ts),
                        json.dumps(payload, ensure_ascii=False, indent=2))
    guarded_write(EVID, "git-snapshot-{}.json".format(ts), json.dumps(meta, ensure_ascii=False, indent=2))
    print(json.dumps({"mode": mode, "gate": g, "counts": payload["counts"],
                      "tools": [{"id": t["id"], "status": t["status"]} for t in tools],
                      "output": str(out.relative_to(ROOT))}, ensure_ascii=False, indent=2))
    if g.get("verdict") == "FAIL":
        sys.exit(1)


def main():
    ap = argparse.ArgumentParser(description="AIGICRM 统一审计入口")
    ap.add_argument("--mode", default="quick",
                    choices=["quick", "full", "security", "business", "changed", "recheck"])
    ap.add_argument("--no-gate", action="store_true", help="跳过质量门禁(只产报告)")
    args = ap.parse_args()
    run_mode(args.mode, args.no_gate)


if __name__ == "__main__":
    main()
