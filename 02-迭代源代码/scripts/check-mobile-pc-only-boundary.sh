#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINI="$ROOT/aigicrm-miniprogram"
MATRIX="$ROOT/docs/function-permission-matrix.md"
SHELL_AUTOMATOR="$ROOT/scripts/miniprogram/verify-mobile-shell.mjs"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_line() {
  local expected="$1"
  grep -Fq "$expected" "$MATRIX" || fail "function permission matrix no longer documents PC-only mobile boundary: $expected"
}

assert_absent() {
  local pattern="$1"
  local reason="$2"
  if rg -n --fixed-strings "$pattern" "$MINI" >/tmp/aigicrm-mobile-pc-only-hit.txt; then
    cat /tmp/aigicrm-mobile-pc-only-hit.txt >&2
    fail "$reason must stay PC-only in the miniprogram"
  fi
}

assert_absent_regex() {
  local pattern="$1"
  local reason="$2"
  if rg -n "$pattern" "$MINI" >/tmp/aigicrm-mobile-pc-only-hit.txt; then
    cat /tmp/aigicrm-mobile-pc-only-hit.txt >&2
    fail "$reason must stay PC-only in the miniprogram"
  fi
}

require_line '| 客户导入 | Import customers | `POST /api/customer/import` | `customer:import` | Import target org/group validation | Import | Yes | No |'
require_line '| 客户导出 | Export customers | `GET /api/customer/export` | `customer:export` | Same list data scope | Export | Yes | No |'
require_line '| 文件夹 ACL | Add/remove ACL | `POST/DELETE /api/file/folders/{id}/acl...` | `file:acl:manage` | Folder ACL manager | ACL mutation | Yes | No first release |'
require_line '| 四项服务 | Create/edit/delete category | `POST/PUT/DELETE /api/incentive/categories...` | `incentive:edit` | Admin/config scope | Config mutation | Yes | No first release |'
require_line '| 四项服务 | Recalculate records | `POST /api/incentive/records/recalculate` | `incentive:edit` | Config/admin scope | Batch mutation | Yes | No |'
require_line '| 激励授权 | Permission list | `GET /api/incentive/permission/list` | `system:config:view` | Config/admin scope | Read | Yes | No |'
require_line '| 激励授权 | Grant/revoke permission | `POST /api/incentive/permission/grant`, `DELETE /api/incentive/permission/{id}` | `system:config:edit` | Config/admin scope | Permission mutation | Yes | No |'
require_line '| 公海导入 | Import to sea | `POST /api/sea/import` | `sea:import` | Target pool import permission | Import | Yes | No |'
require_line '| 公海配置 | Config list | `GET /api/sea/configs` | `sea:config:view` | Config/admin scope | Read | Yes | No |'
require_line '| 公海配置 | Org tree for config | `GET /api/sea/org-tree` | `sea:config:view` | Config/admin scope | Read sensitive org/person tree | Yes | No |'
require_line '| 审批配置 | Config list | `GET /api/approval/config/list` | `system:config:view` | Config/admin scope | Read | Yes | No |'
require_line '| 学分录入 | Grid | `GET /api/score/grid` | `score:manage` | Managed users/depts | Read | Yes | No |'
require_line '| 学分录入 | Batch entry | `POST /api/score/entries/batch` | `score:entry` | Managed users/depts | Batch mutation | Yes | No |'
require_line '| 学分导出 | Export grid | `GET /api/score/export/grid` | `score:manage` | Same grid/ranking scope | Export | Yes | No |'

assert_absent '/api/customer/import' 'customer import'
assert_absent '/api/customer/export' 'customer export'
assert_absent '/api/sea/import' 'public sea import'
assert_absent '/api/sea/org-tree' 'public sea config org tree'
assert_absent '/api/approval/config' 'approval configuration'
assert_absent '/api/incentive/records/recalculate' 'incentive batch recalculation'
assert_absent '/api/incentive/permission/list' 'incentive permission administration'
assert_absent '/api/incentive/permission/grant' 'incentive permission grant'
# 【2026-11-06 重锚】/api/score/grid 只读明细源放开(MP 学分明细页拼逐项得分, score:view 同门,
# 与排行同受众同粒度级; 录入面=PC)。仍禁: 批量写/导出/录入码——PC-only 录入边界原样
assert_absent '/api/score/entries/batch' 'score batch entry'
assert_absent '/api/score/export/grid' 'score export'
if rg -n "/api/incentive/categories[^[:alnum:]_/].*method: '(POST|PUT|DELETE)'|/api/incentive/categories[^[:alnum:]_/].*method: \"(POST|PUT|DELETE)\"" "$MINI" >/tmp/aigicrm-mobile-pc-only-hit.txt; then
  cat /tmp/aigicrm-mobile-pc-only-hit.txt >&2
  fail "incentive category mutation must stay PC-only in the miniprogram"
fi
assert_absent_regex "/api/file/folders/.*'/acl.*/(POST|DELETE|PUT)|/api/file/folders/.*\"/acl.*/(POST|DELETE|PUT)" 'file folder ACL mutation'

grep -Fq 'verifyPcOnlyBoundary' "$SHELL_AUTOMATOR" \
  || fail "mobile shell automator must verify PC-only boundary at runtime"
grep -Fq 'mobile-shell-pc-only-boundary.png' "$SHELL_AUTOMATOR" \
  || fail "mobile shell automator must capture PC-only boundary evidence"
grep -Fq 'customer:import' "$SHELL_AUTOMATOR" \
  || fail "mobile shell automator must test customer import permission does not expose mobile entry"
grep -Fq 'score:entry' "$SHELL_AUTOMATOR" \
  || fail "mobile shell automator must test score entry permission does not expose mobile entry"
grep -Fq 'system:config:view' "$SHELL_AUTOMATOR" \
  || fail "mobile shell automator must test system/config permission does not expose mobile entry"

if rg -n "/api/system/(roles|positions|groups|menus|configs|logs|rules|perm-scopes)|/api/system/users([^/[:alnum:]-]|/[0-9]+|/.*/reset-password)" "$MINI" >/tmp/aigicrm-mobile-pc-only-hit.txt; then
  cat /tmp/aigicrm-mobile-pc-only-hit.txt >&2
  fail "system administration APIs must stay PC-only in the miniprogram"
fi

if rg -n '/api/sea/configs' "$MINI" | grep -v '/api/sea/configs/visible' >/tmp/aigicrm-mobile-pc-only-hit.txt; then
  cat /tmp/aigicrm-mobile-pc-only-hit.txt >&2
  fail "public sea configuration APIs must stay PC-only; mobile may only use /api/sea/configs/visible"
fi

echo "PASS: mobile PC-only boundary keeps import/export, config, ACL mutation, system admin, and batch operations out of the miniprogram."
