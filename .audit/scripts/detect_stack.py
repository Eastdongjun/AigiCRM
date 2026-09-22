#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""技术栈自动识别(规范§4):项目标记文件 → .audit/evidence/stack-profile.json。
文件宇宙=git 受控文件,不做磁盘自由遍历;后续扫描器选择完全由该 profile 驱动。"""
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(os.getcwd()).resolve()
if not (ROOT / ".audit" / "config" / "audit.config.yaml").is_file():
    raise SystemExit("请在项目根目录运行(根下须有 .audit/config/)")
EVID = ROOT / ".audit" / "evidence"


def guarded_write(filename, text):
    target = EVID / filename
    if EVID.resolve() not in target.resolve().parents:
        raise SystemExit("非法写出路径: " + filename)
    EVID.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return target


def tracked_files():
    out = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files"],
                         cwd=str(ROOT), capture_output=True, text=True, timeout=120)
    if out.returncode != 0:
        raise SystemExit("git ls-files 失败: " + out.stderr.strip()[:200])
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def read_text(rel):
    try:
        return (ROOT / rel).read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def build_profile():
    """构造技术栈档案(纯函数,供 CLI 与 audit.py 直调;不再经由子进程)。"""
    files = tracked_files()
    src = "02-迭代源代码/"
    profile = {"generated_at": datetime.now(timezone.utc).isoformat(),
               "workspace_root": ROOT.name, "project_type": None,
               "frontend": None, "backend": None, "miniprogram": None,
               "services": [], "database": [], "infrastructure": [], "api": [],
               "scanners_expected": []}

    def names(): return [os.path.basename(p) for p in files]

    if "pom.xml" in names():
        text = read_text(src + "aigicrm-backend/pom.xml")
        m = re.search(r"<java.version>([^<]+)", text)
        ver = re.search(r"spring-boot-starter-parent</artifactId>\s*<version>([^<]+)", text)
        libs = [lib for lib in ("mybatis-plus", "flyway-core", "jjwt", "redis", "mysql",
                                "spring-boot-starter-security") if lib in text]
        profile["backend"] = {"language": "java", "language_version": m.group(1) if m else None,
                              "framework": "spring-boot", "framework_version": ver.group(1) if ver else None,
                              "build_tool": "maven", "libraries": libs}
    elif "go.mod" in names():
        profile["backend"] = {"language": "go", "build_tool": "go"}
    elif "requirements.txt" in names() or "pyproject.toml" in names():
        profile["backend"] = {"language": "python", "build_tool": "pip"}

    pkg_rel = src + "aigicrm-ui/package.json"
    if pkg_rel in files:
        pkg = json.loads(read_text(pkg_rel) or "{}")
        deps = {**(pkg.get("dependencies") or {}), **(pkg.get("devDependencies") or {})}
        ui_src = src + "aigicrm-ui/src/"
        profile["frontend"] = {
            "name": pkg.get("name"),
            "framework": "vue" if "vue" in deps else ("react" if "react" in deps else None),
            "vue": deps.get("vue"), "typescript": "typescript" in deps,
            "build_tool": "vite" if "vite" in deps else None,
            "test": "vitest" if "vitest" in deps else None,
            "linter": "eslint" if "eslint" in deps else None,
            "package_manager": ("pnpm" if "pnpm-lock.yaml" in names()
                                else "yarn" if "yarn.lock" in names()
                                else "npm" if "package-lock.json" in names() else "npm(no-lockfile)"),
            "css": [ext for ext in ("scss", "less")
                    if any(p.startswith(ui_src) and p.endswith("." + ext) for p in files)]}

    mini_app = src + "aigicrm-miniprogram/app.json"
    if mini_app in files:
        try:
            appid = json.loads(read_text(src + "aigicrm-miniprogram/project.config.json")).get("appid")
        except Exception:
            appid = None
        profile["miniprogram"] = {
            "platform": "wechat", "appid_present": bool(appid),
            "pages": len(json.loads(read_text(mini_app)).get("pages", [])),
            "has_cloudfunctions": any(
                p.startswith(src + "aigicrm-miniprogram/cloudfunctions/") for p in files)}

    mig_prefix = src + "aigicrm-backend/src/main/resources/db/migration/"
    migs = [p for p in files if p.startswith(mig_prefix) and p.endswith(".sql")]
    if migs:
        profile["database"].append({"engine": "mysql", "migrations": len(migs), "tool": "flyway"})

    if (src + "docker-compose.yml") in files:
        profile["infrastructure"] += ["docker", "docker-compose"]
    if (src + "docker-compose.prod.yml") in files:
        profile["infrastructure"].append("docker-compose(prod)")
    if any(os.path.basename(p) == "Dockerfile" or os.path.basename(p).startswith("Dockerfile.")
           for p in files if p.startswith(src)):
        profile["infrastructure"].append("dockerfile")
    if any(p.startswith(src + "nginx/") and p.endswith(".conf") for p in files):
        profile["infrastructure"].append("nginx")
    if any(re.search(r"(openapi|swagger)\.(yaml|json)$", p) for p in files):
        profile["api"].append("openapi")
    else:
        profile["api"].append("spring-annotations(隐式)")

    kinds = [k for k, v in (("frontend", profile["frontend"]), ("backend", profile["backend"]),
                            ("miniprogram", profile["miniprogram"])) if v]
    profile["project_type"] = "fullstack" if len(kinds) >= 2 else (kinds[0] if kinds else "unknown")

    exp = []
    if profile["backend"]:
        exp += {"java": ["sonar", "spotbugs", "pmd", "semgrep", "codeql", "dependency-check", "trivy"],
                "go": ["gofmt", "go-vet", "staticcheck", "gosec", "govulncheck"],
                "python": ["ruff", "pylint", "bandit", "pip-audit"]}.get(profile["backend"]["language"], [])
    if profile["frontend"]:
        exp += ["eslint", "eslint-plugin-vue", "tsc", "semgrep", "codeql", "osv", "trivy", "vitest", "stylelint"]
    if profile["miniprogram"]:
        exp += ["mini-static(自有)", "wechat-devtools-cli"]
    exp += ["gitleaks", "trivy-secret", "hadolint", "checkov"]
    profile["scanners_expected"] = sorted(set(exp))
    return profile


def main():
    payload = json.dumps(build_profile(), ensure_ascii=False, indent=2)
    guarded_write("stack-profile.json", payload)
    print(payload)
    print("[ok] stack-profile → .audit/evidence/stack-profile.json", file=sys.stderr)


if __name__ == "__main__":
    main()
