#!/bin/bash
# 文档新鲜度检查（2026-08-08, 审查发现 #3）:
# 重新生成自动文档到临时目录 → diff 已提交版本 → 有差异 = 文档滞后 = FAIL
# 用法: ./check-doc-freshness.sh（默认检查生产环境）
# 本地联调: DOC_FRESHNESS_MODE=local LOCAL_DB_PASSWORD=... ./check-doc-freshness.sh
# 说明: 数据字典/权限字典从生产库实时生成(只读), API 清单从源码生成; 均不改动已提交文档。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set +e

REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
TMPDIR=$(mktemp -d /tmp/docgen.XXXXXX)
trap 'rm -rf "$TMPDIR"' EXIT
FAIL=0
MODE="${DOC_FRESHNESS_MODE:-prod}"

case "$MODE" in
  prod|test)
    # Remote modes retain the existing deployment-environment resolution.
    source "$SCRIPT_DIR/deploy-lib.sh"
    ;;
  local) ;;
  *)
    echo "❌ DOC_FRESHNESS_MODE 必须是 prod、test 或 local，当前为: $MODE"
    exit 2
    ;;
esac

compare_doc() {
  local gen="$1" committed="$2" label="$3"
  if [ ! -f "$gen" ]; then
    echo "❌ $label 生成失败(临时文件缺失)"; FAIL=$((FAIL+1)); return
  fi
  if [ ! -f "$committed" ]; then
    echo "❌ $label 已提交文档缺失: $committed"; FAIL=$((FAIL+1)); return
  fi
  if diff -q "$gen" "$committed" >/dev/null 2>&1; then
    echo "✅ $label 最新"
  else
    echo "❌ $label 已滞后 —— 请重新运行对应 gen 脚本并提交更新"
    FAIL=$((FAIL+1))
  fi
}

echo "--- [7] 文档新鲜度(模式: $MODE, 重新生成 diff 已提交版本) ---"

# 数据字典(生产库)
if OUT="$TMPDIR/data-dictionary.md" bash "$SCRIPT_DIR/gen-data-dictionary.sh" "$MODE" >/dev/null 2>&1; then
  compare_doc "$TMPDIR/data-dictionary.md" "$REPO/docs/database/data-dictionary.md" "数据字典 data-dictionary.md"
else
  echo "⚠️ 数据字典 data-dictionary.md 未完成：数据库生成失败或凭据不可用"
  FAIL=$((FAIL+1))
fi

# 权限字典(生产库)
if OUT="$TMPDIR/permission-dict.md" bash "$SCRIPT_DIR/gen-permission-dict.sh" "$MODE" >/dev/null 2>&1; then
  compare_doc "$TMPDIR/permission-dict.md" "$REPO/docs/permission/permission-dict.md" "权限字典 permission-dict.md"
else
  echo "⚠️ 权限字典 permission-dict.md 未完成：数据库生成失败或凭据不可用"
  FAIL=$((FAIL+1))
fi

# API 清单(源码)
# Controllers also live outside the module packages (for example system tasks),
# so the freshness check must scan the whole backend source tree.
python3 "$SCRIPT_DIR/gen-api.py" "$REPO/aigicrm-backend/src/main/java" "$TMPDIR/api-list.md" >/dev/null 2>&1
compare_doc "$TMPDIR/api-list.md" "$REPO/docs/api/api-list.md" "API清单 api-list.md"

if [ "$FAIL" -gt 0 ]; then
  echo "文档新鲜度: FAIL=$FAIL"
  echo "❌ 存在滞后文档 —— 更新后再交付"
  exit 1
fi
echo "文档新鲜度: PASS"
echo "✅ 文档全部最新"
exit 0
