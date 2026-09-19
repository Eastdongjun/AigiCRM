#!/usr/bin/env bash
# Keep in-flight Flyway migrations covered by local migration and permission contracts.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"
AUDIT="$ROOT/scripts/gen-unified-feature-audit.py"
REG="$ROOT/scripts/check-permission-registration-contract.py"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

for file in \
  "$MIG/V2026.09.13.1__register_missing_used_permission_codes.sql" \
  "$MIG/V2026.09.13.2__fix_menu_route_correspondence.sql" \
  "$MIG/V2026.09.13.3__remove_stale_incentive_view_all_scope.sql" \
  "$MIG/V2026.09.13.4__add_incentive_read_ranking_menu.sql" \
  "$AUDIT" \
  "$REG"; do
  [ -f "$file" ] || fail "Missing $file"
done

migration_output="$(bash "$ROOT/scripts/check-migration-contract.sh")"
printf '%s\n' "$migration_output" | grep -Fq '41 个迁移' \
  || fail "migration contract did not scan the full current migration set: $migration_output"

registration_output="$(python3 "$REG")"
printf '%s\n' "$registration_output" | grep -Fq '代码在用权限码 64 个' \
  || fail "permission registration contract did not scan the current code permission set: $registration_output"
printf '%s\n' "$registration_output" | grep -Fq '未登记或未启用 0 个' \
  || fail "permission registration contract found missing or disabled permission codes: $registration_output"

python3 - "$ROOT" <<'PY'
import importlib.util
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
script = root / "scripts/gen-unified-feature-audit.py"
spec = importlib.util.spec_from_file_location("aigicrm_feature_audit", script)
module = importlib.util.module_from_spec(spec)
sys.modules["aigicrm_feature_audit"] = module
spec.loader.exec_module(module)

permissions = module.extract_migration_permissions(root / "aigicrm-backend/src/main/resources/db/migration")
required = {
    "incentive:read-stats": "激励查询已读统计",
    "contact:view": "客户联系人查看",
    "finance:view": "客户财务查看",
    "customer:date:edit": "客户日期编辑",
    "incentive:read-ranking:view": "本月激励查询阅读排行榜",
}
missing = []
for permission, label in required.items():
    source = permissions.get(permission, "")
    if label not in source:
        missing.append(f"{permission} -> {source!r}")
if missing:
    raise SystemExit("missing migration permission source labels: " + "; ".join(missing))
PY

echo "PASS: in-flight Flyway migrations, permission registration, and feature-audit migration parsing are aligned."
