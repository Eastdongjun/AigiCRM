#!/usr/bin/env bash
# YCMN 全量验证：后端编译+单测 → 前端测试+构建 → 三脚本交叉核对
# 用法: ./verify.sh   （任何一步失败即退出非 0）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
J17=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
if [ ! -d "$J17" ]; then
  echo "[verify] 未找到 JDK 17: $J17"; exit 1
fi
export JAVA_HOME="$J17"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> [1/5] 后端编译+单测 (JDK 17)"
(cd "$ROOT/ycmn-backend" && mvn -q test)

echo "==> [2/5] 前端单测"
if [ -f "$ROOT/ycmn-ui/package.json" ] && grep -q '"test"' "$ROOT/ycmn-ui/package.json"; then
  (cd "$ROOT/ycmn-ui" && npm run test)
else
  echo "     (前端暂无测试脚本，跳过)"
fi

echo "==> [3/5] 前端生产构建"
(cd "$ROOT/ycmn-ui" && npm run build)

SKILL="$HOME/.codex/skills/code-review/scripts"
echo "==> [4/5] schema/API/权限 三脚本交叉核对（告警不阻塞）"
python3 "$SKILL/check_schema_entity.py" --java-dir "$ROOT/ycmn-backend/src/main/java" --sql "$ROOT/ycmn_database_test.sql" || true
python3 "$SKILL/check_api_wiring.py" --frontend-api "$ROOT/ycmn-ui/src/api" --backend "$ROOT/ycmn-backend/src/main/java" || true
python3 "$SKILL/check_permission_strings.py" --backend "$ROOT/ycmn-backend/src/main/java" --sql "$ROOT/ycmn_database_test.sql" || true

echo "==> [5/5] 集成测试（MySQL 3308 在线时才跑）"
if (nc -z -w2 localhost 3308 >/dev/null 2>&1); then
  (cd "$ROOT/ycmn-backend" && mvn -q test -Dgroups=integration)
else
  echo "     (MySQL 3308 未启动，跳过; docker-compose up -d 后可运行)"
fi

echo "==> ✅ 全量验证完成"
