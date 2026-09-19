#!/usr/bin/env bash
# Keep the untracked inventory useful for delivery review.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_VERIFY="$ROOT/scripts/dev-verify.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ -f "$DEV_VERIFY" ] || fail "Missing $DEV_VERIFY"

output="$(bash "$DEV_VERIFY" untracked)"

printf '%s\n' "$output" | grep -Fq 'AIGICRM untracked inventory' ||
  fail "untracked inventory must have a recognizable title"

printf '%s\n' "$output" | grep -Eq 'summary: total=[0-9]+; migrations=[0-9]+; backend=[0-9]+; frontend=[0-9]+; miniprogram=[0-9]+; scripts=[0-9]+; docs=[0-9]+; config=[0-9]+; other=[0-9]+' ||
  fail "untracked inventory must show the standard category summary"

for category in migrations backend frontend miniprogram scripts docs config other; do
  printf '%s\n' "$output" | grep -Eq "^  ${category} \\([0-9]+\\):$" ||
    fail "untracked inventory must include the ${category} category"
done

# 【2026-09-19 修正】原两条断言把"写契约当刻的未跟踪状态"固化为常真条件——
# （当时未跟踪迁移 V2026.09.13.x 在库、本契约脚本自身也未跟踪），全量提交后必然误红。
# 改为与 summary 计数一致的双向断言：计数>0 必须列出对应条目（守护清单机制不失效）；
# 计数=0 不得出现迁移路径（守护不虚报）。
untracked_migrations="$(printf '%s\n' "$output" | sed -nE 's/.*summary: total=[0-9]+; migrations=([0-9]+).*/\1/p' | head -1)"
[ -n "$untracked_migrations" ] || untracked_migrations=0
if [ "$untracked_migrations" -gt 0 ]; then
  printf '%s\n' "$output" | grep -Fq 'aigicrm-backend/src/main/resources/db/migration/V' ||
    fail "untracked inventory must list untracked Flyway migrations when present"
elif printf '%s\n' "$output" | grep -Fq 'aigicrm-backend/src/main/resources/db/migration/V'; then
  fail "untracked inventory listed Flyway migrations while summary says migrations=0"
fi

untracked_scripts="$(printf '%s\n' "$output" | sed -nE 's/.*summary: total=[0-9]+; scripts=([0-9]+).*/\1/p' | head -1)"
[ -n "$untracked_scripts" ] || untracked_scripts=0
if [ "$untracked_scripts" -gt 0 ]; then
  printf '%s\n' "$output" | grep -Eq '^    - scripts/' ||
    fail "untracked inventory must list untracked script entries when present"
fi

for batch in database-permissions backend-regression frontend-features automation-contracts delivery-docs; do
  printf '%s\n' "$output" | grep -Fq "    - ${batch}:" ||
    fail "untracked inventory must include the ${batch} review batch"
done

echo "PASS: dev-verify untracked inventory is categorized and reviewable."
