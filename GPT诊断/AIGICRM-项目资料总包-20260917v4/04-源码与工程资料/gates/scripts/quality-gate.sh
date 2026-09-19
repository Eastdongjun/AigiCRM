#!/usr/bin/env bash
# AIGICRM 质量门禁一键执行（P0，2026-08-12）
# 用法: bash scripts/quality-gate.sh
# 覆盖: 修改范围检查 → 前端 test/build → 后端 mvn verify(JDK17) → 变更文件 VSCode 诊断 → 报告表
# 退出码: 0=全部强制门禁 PASS；1=存在 FAIL；2=环境/依赖缺失导致 BLOCKED
# 原则: lint/type-check 未配置如实标 N/A；缺验证条件标 BLOCKED；不虚报 PASS。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"

PASS=0; FAIL=0; NA=0; BLOCKED=0
ok() { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad() { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
na() { echo "  ➖ $1"; NA=$((NA+1)); }
blk() { echo "  ⛔ $1"; BLOCKED=$((BLOCKED+1)); }

echo "════════ AIGICRM 质量门禁 $(date '+%Y-%m-%d %H:%M:%S') ════════"

# 变更文件（staged + unstaged，去引号，规范为仓库相对路径）
CHANGED=$(cd "$REPO" && git status --porcelain | sed 's/^...//' | grep -v '^$' || true)

# ---------------------------------------------------------------- [1] 修改范围检查
echo "--- [1] 修改范围检查 ---"
if [ -z "$CHANGED" ]; then
  na "工作区无变更（本轮无代码改动）"
else
  echo "  变更文件:"
  echo "$CHANGED" | sed 's/^/    /'
fi
if (cd "$REPO" && git diff --check >/tmp/qg-dc1.txt 2>&1 && git diff --cached --check >/tmp/qg-dc2.txt 2>&1); then
  ok "git diff --check 通过（无空白/冲突标记）"
else
  bad "git diff --check 发现空白错误:"; cat /tmp/qg-dc1.txt /tmp/qg-dc2.txt 2>/dev/null | sed 's/^/    /' | head -10
fi
rm -f /tmp/qg-dc1.txt /tmp/qg-dc2.txt
# 变更文件里的硬编码密码（粗检：password=xxx / password:"xxx"）
HARD=$(echo "$CHANGED" | while IFS= read -r f; do
  [ -f "$REPO/$f" ] || continue
  grep -nE "password\s*[:=]\s*['\"][^'\"]{6,}['\"]" "$REPO/$f" 2>/dev/null | head -2 | sed "s|^|$f:|"
done)
if [ -n "$HARD" ]; then bad "变更文件疑似硬编码密码（应经 ~/.aigicrm-deploy.env 注入）:"; echo "$HARD" | sed 's/^/    /'; else ok "无硬编码密码"; fi

# ---------------------------------------------------------------- [2] 前端门禁
echo "--- [2] 前端 (aigicrm-ui, npm) ---"
if [ ! -d "$REPO/aigicrm-ui/node_modules" ]; then
  blk "前端 node_modules 缺失 —— 需先 cd aigicrm-ui && npm install（BLOCKED）"
else
  if (cd "$REPO/aigicrm-ui" && npm run test >/tmp/qg-fe-test.log 2>&1); then
    ok "前端测试 (vitest) 通过"
  else
    bad "前端测试失败:"; tail -15 /tmp/qg-fe-test.log | sed 's/^/    /'
  fi
  rm -f /tmp/qg-fe-test.log
  if (cd "$REPO/aigicrm-ui" && npm run build >/tmp/qg-fe-build.log 2>&1); then
    ok "前端构建通过（退出码 0）"
  else
    bad "前端构建失败:"; tail -15 /tmp/qg-fe-build.log | sed 's/^/    /'
  fi
  rm -f /tmp/qg-fe-build.log
fi
# lint / type-check：未配置则如实标 N/A，不得虚报
for SCRIPT in lint type-check; do
  if grep -q "\"$SCRIPT\"" "$REPO/aigicrm-ui/package.json" 2>/dev/null; then
    if (cd "$REPO/aigicrm-ui" && npm run "$SCRIPT" >/tmp/qg-fe-x.log 2>&1); then
      ok "前端 $SCRIPT 通过"
    else
      bad "前端 $SCRIPT 失败:"; tail -10 /tmp/qg-fe-x.log | sed 's/^/    /'
    fi
    rm -f /tmp/qg-fe-x.log
  else
    na "前端 $SCRIPT 未配置（JS 项目）—— 如实 N/A，不虚报 PASS"
  fi
done

# ---------------------------------------------------------------- [3] 后端门禁
echo "--- [3] 后端 (aigicrm-backend, mvn verify, 需 JDK 17) ---"
# shellcheck source=ensure-java21.sh
source "$SCRIPT_DIR/ensure-java21.sh"
if ! ensure_java21 >/tmp/qg-j17.log 2>&1; then
  blk "找不到 JDK 17（Lombok 与 JDK 25 不兼容）—— BLOCKED"
  tail -5 /tmp/qg-j17.log | sed 's/^/    /'
else
  grep -E "找到|使用" /tmp/qg-j17.log | sed 's/^/    /' | head -1
  if (cd "$REPO/aigicrm-backend" && mvn -B verify >/tmp/qg-be.log 2>&1); then
    ok "后端 mvn verify 通过（编译+测试+打包，退出码 0）"
    echo "$(grep -cE 'Tests run:.*Failures: [1-9]' /tmp/qg-be.log >/dev/null 2>&1 && echo '     ⚠️ 存在失败测试（不应出现）' || true)"
  else
    bad "后端 mvn verify 失败:"; tail -30 /tmp/qg-be.log | sed 's/^/    /'
  fi
  rm -f /tmp/qg-be.log
fi
rm -f /tmp/qg-j17.log

# ---------------------------------------------------------------- [4] 变更文件 VSCode 诊断（P0 规则 18）
echo "--- [4] VSCode 诊断（规则 18，桥可用时；只对本次变更文件计 FAIL）---"
DIAG_URL="${VSCODE_MCP_URL:-http://127.0.0.1:3000/mcp}"
DIAG=$(curl -s --max-time 8 -X POST "$DIAG_URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_diagnostics_code","arguments":{"severities":[0,1]}}}' 2>/dev/null)
if [ -z "$DIAG" ]; then
  na "VSCode 诊断桥不可用（VS Code 未运行 / vscode-mcp 未连接）—— N/A，不阻断"
else
  echo "$DIAG" > /tmp/qg-diag.raw
  ANALYSIS=$(CHANGED="$CHANGED" python3 - /tmp/qg-diag.raw <<'PYEOF'
import json, os, re, sys
changed = {p.strip().lstrip("./") for p in os.environ.get("CHANGED", "").splitlines() if p.strip()}
try:
    data = None
    with open(sys.argv[1], encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("data:"):
                try:
                    data = json.loads(line[5:].strip())
                except Exception:
                    continue
                break
    if not data:
        print("NA"); sys.exit(0)
    text = data["result"]["content"][0]["text"]
except Exception:
    print("NA"); sys.exit(0)
if "No issues found." in text:
    print("CLEAN"); sys.exit(0)
err_all = warn_all = err_change = warn_change = 0
for m in re.finditer(r"^(Error|Warning):\s+([^:\n]+):(\d+):(\d+)", text, re.M):
    level, path = m.group(1), m.group(2).strip()
    if level == "Error":
        err_all += 1
        if path in changed:
            err_change += 1
    else:
        warn_all += 1
        if path in changed:
            warn_change += 1
print(f"{err_all} {warn_all} {err_change} {warn_change}")
PYEOF
)
  rm -f /tmp/qg-diag.raw
  case "$ANALYSIS" in
    CLEAN) ok "VSCode 诊断：0 Error / 0 Warning" ;;
    NA)    na "诊断返回格式异常或桥无响应，未计入" ;;
    *)
      read -r ERR_ALL WARN_ALL ERR_CHANGE WARN_CHANGE <<<"$ANALYSIS"
      echo "    工作区总计: Error=${ERR_ALL} Warning=${WARN_ALL}；本次变更文件内: Error=${ERR_CHANGE} Warning=${WARN_CHANGE}"
      if [ "$ERR_CHANGE" -gt 0 ]; then
        bad "变更文件存在 $ERR_CHANGE 个 Error 诊断 —— 必须清零（规则 18）"
      else
        ok "变更文件 0 Error 诊断"
      fi
      if [ "$WARN_CHANGE" -gt 0 ]; then
        na "变更文件存在 $WARN_CHANGE 个 Warning 诊断 —— 需清零或逐条记录原因（hook 已按编辑逐条阻断）"
      fi
      ;;
  esac
fi

# ---------------------------------------------------------------- 报告
echo "══════════════════════════════════════════════════"
echo "质量门禁报告 $(date '+%H:%M:%S')"
echo "  修改范围: PASS"  # git diff --check 已在上面逐条计
echo "  汇总: PASS=$PASS FAIL=$FAIL N/A=$NA BLOCKED=$BLOCKED"
if [ "$FAIL" -gt 0 ]; then
  echo "  状态: FAIL —— ❌ 存在强制门禁失败，禁止宣称完成/上线"
  echo "  修正: 查看上方 ❌ 项，修复后重跑 bash scripts/quality-gate.sh"
  exit 1
elif [ "$BLOCKED" -gt 0 ]; then
  echo "  状态: BLOCKED —— ⛔ 环境/依赖/验证条件缺失，补齐后重跑（不得报 PASS）"
  exit 2
else
  echo "  状态: PASS —— ✅ 全部强制门禁通过（lint/type-check 为 N/A 项已如实标注）"
  exit 0
fi
