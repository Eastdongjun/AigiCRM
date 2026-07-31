#!/usr/bin/env bash
# 会话启动交叉核对：schema/API/权限三脚本，只告警不阻塞
set -u
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
SKILL="$HOME/.codex/skills/code-review/scripts"
echo "===== codex session-check: schema/entity ====="
python3 "$SKILL/check_schema_entity.py" \
  --java-dir "$PROJ/ycmn-backend/src/main/java" \
  --sql "$PROJ/ycmn_database_test.sql" || true
echo "===== codex session-check: api wiring ====="
python3 "$SKILL/check_api_wiring.py" \
  --frontend-api "$PROJ/ycmn-ui/src/api" \
  --backend "$PROJ/ycmn-backend/src/main/java" || true
echo "===== codex session-check: permission strings ====="
python3 "$SKILL/check_permission_strings.py" \
  --backend "$PROJ/ycmn-backend/src/main/java" \
  --sql "$PROJ/ycmn_database_test.sql" || true
exit 0
