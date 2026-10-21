#!/usr/bin/env bash
# Guard daily development automation against accidental remote or production actions.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

targets=(
  ".githooks/pre-push"
  ".vscode/extensions.json"
  ".vscode/settings.json"
  ".vscode/tasks.json"
  "scripts/dev-evidence.sh"
  "scripts/dev-verify.sh"
  "scripts/install-git-hooks.sh"
  "scripts/verify-sea-claim-matrix.sh"
)

# 【审计 F1 2026-10-21】生产浏览器冒烟脚本(aigicrm-ui/*smoke*.mjs, 曾硬编码生产地址与账号口令)
#   纳入守卫目标: 跟踪+未忽略的冒烟脚本动态枚举, 新增脚本自动覆盖, 不必逐个登记。
while IFS= read -r rel; do
  [ -n "$rel" ] || continue
  targets+=("$rel")
done < <(
  git -C "$ROOT" -c core.quotePath=false ls-files --cached --others --exclude-standard -- aigicrm-ui 2>/dev/null |
    grep -E '^aigicrm-ui/[^/]*smoke[^/]*\.mjs$' || true
)

forbidden='(^|[;&|[:space:]])(ssh|scp|rsync|mysql|flyway|docker([[:space:]]+compose)?|kubectl|helm)([[:space:]]|$)|git[[:space:]]+(push|commit|add|reset|checkout)|deploy-(backend|frontend|db)|auto-pipeline|smoke-test\.sh[[:space:]]+prod|prod(uction)?[[:space:]]+(deploy|migration|smoke)'

status=0
for rel in "${targets[@]}"; do
  path="$ROOT/$rel"
  [ -f "$path" ] || continue

  # 【2026-09-12 加固】原写法 `grep -nE "$forbidden" || true` 把**两种**非零退出
  #   一律吞掉：exit 1 = 无匹配（正常），exit 2 = 正则/IO 错误（异常）。
  #   若 $forbidden 变成非法正则，grep 退 2 却被 `|| true` 掩盖 ⇒
  #   安全守卫**静默通过**（假绿）。实测当前 pattern 可编译（退 1 而非 2），
  #   故风险为 DORMANT；此处按 fail-closed 加固，避免日后变成真实缺陷。
  set +e
  matches="$(
    sed -E '/^[[:space:]]*#/d; s/#.*$//' "$path" |
      grep -nE "$forbidden"
  )"
  rc=$?
  set -e
  if [ "$rc" -eq 2 ]; then
    echo "FAIL: 禁用模式正则/IO 错误（安全守卫无法执行，拒绝静默通过）: $rel" >&2
    status=1
    continue
  fi
  if [ -n "$matches" ]; then
    echo "FAIL: unsafe automation command pattern in $rel" >&2
    echo "$matches" >&2
    status=1
  fi
done

if [ "$status" -eq 0 ]; then
  echo "PASS: daily development automation contains no forbidden remote/deploy patterns."
fi

exit "$status"
