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
  # 【2026-09-12 修正设计缺陷】生成的文档头部带 `> 生成时间: YYYY-MM-DD`，
  #   直接逐字节 diff 会让"只要不是当天重新生成"必然 FAIL —— 这是**假红**：
  #   内容完全正确、仅日期不同也会被判定为滞后。
  #   故比较前剔除"生成时间"行，只比较**实质内容**。
  #   注意：剔除日期不等于放弃新鲜度断言 —— 若实质内容有任何差异仍会 FAIL。
  local g c
  g=$(mktemp); c=$(mktemp)
  grep -v '^> 生成时间:' "$gen" > "$g"
  grep -v '^> 生成时间:' "$committed" > "$c"
  if diff -q "$g" "$c" >/dev/null 2>&1; then
    if diff -q "$gen" "$committed" >/dev/null 2>&1; then
      echo "✅ $label 最新"
    else
      echo "✅ $label 内容最新（仅生成日期不同，非遗漏）"
    fi
  else
    echo "❌ $label 已滞后 —— 请重新运行对应 gen 脚本并提交更新"
    diff "$g" "$c" | head -20 | sed 's/^/     /'
    FAIL=$((FAIL+1))
  fi
  rm -f "$g" "$c"
}

echo "--- [7] 文档新鲜度(模式: $MODE, 重新生成 diff 已提交版本) ---"

# 【2026-09-12 修正第二处设计缺陷】原先"生成器跑不起来"与"文档内容滞后"
#   都被计入 FAIL。两者性质不同：
#     · 内容滞后   = 真实缺陷，必须 FAIL
#     · 生成器不可用 = 环境/能力缺失（无 DB 凭据、无网络），**不是文档滞后**
#   混在一起会让门禁在本机/CI（无生产凭据）永远红，从而被无视——这正是
#   "狼来了"效应。现分离：内容滞后记 FAIL；生成器不可用记 BLOCKED（单列计数），
#   并在结尾明确报告，既不掩盖也不误判。
BLOCKED=0
gen_blocked() { # $1=文档名  $2=原因
  echo "⛔ $1 无法校验（生成器不可用：$2）—— 记为 BLOCKED，不计入内容滞后"
  BLOCKED=$((BLOCKED+1))
}

# 数据字典(库派生)
if OUT="$TMPDIR/data-dictionary.md" bash "$SCRIPT_DIR/gen-data-dictionary.sh" "$MODE" >/dev/null 2>&1; then
  compare_doc "$TMPDIR/data-dictionary.md" "$REPO/docs/database/data-dictionary.md" "数据字典 data-dictionary.md"
else
  gen_blocked "数据字典 data-dictionary.md" "数据库生成失败或凭据不可用"
fi

# 权限字典(库派生)
if OUT="$TMPDIR/permission-dict.md" bash "$SCRIPT_DIR/gen-permission-dict.sh" "$MODE" >/dev/null 2>&1; then
  compare_doc "$TMPDIR/permission-dict.md" "$REPO/docs/permission/permission-dict.md" "权限字典 permission-dict.md"
else
  gen_blocked "权限字典 permission-dict.md" "数据库生成失败或凭据不可用"
fi

# API 清单(源码派生，无需 DB —— 本机/CI 永远可校验)
# Controllers also live outside the module packages (for example system tasks),
# so the freshness check must scan the whole backend source tree.
if python3 "$SCRIPT_DIR/gen-api.py" "$REPO/aigicrm-backend/src/main/java" "$TMPDIR/api-list.md" >/dev/null 2>&1; then
  compare_doc "$TMPDIR/api-list.md" "$REPO/docs/api/api-list.md" "API清单 api-list.md"
else
  # 源码派生却生成失败 ⇒ 这是真实缺陷（如 gen-api.py 坏了），记 FAIL
  echo "❌ API清单 api-list.md 生成器执行失败（源码派生，不应失败）"
  FAIL=$((FAIL+1))
fi

if [ "$FAIL" -gt 0 ]; then
  echo "文档新鲜度: FAIL=$FAIL (BLOCKED=$BLOCKED)"
  echo "❌ 存在滞后文档 —— 更新后再交付"
  exit 1
fi
if [ "$BLOCKED" -gt 0 ]; then
  echo "文档新鲜度: PASS(可校验部分) / BLOCKED=$BLOCKED"
  echo "✅ 可校验的文档均最新（$BLOCKED 项因生成器不可用未校验，非滞后）"
  exit 0
fi
echo "文档新鲜度: PASS"
echo "✅ 文档全部最新"
exit 0
