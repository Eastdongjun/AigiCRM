#!/usr/bin/env bash
# 【GL-16 2026-11-24】法布施已废止的名称不得再出现在用户看得到的源码里。
#   内容治理 → 内容管理（30640a6c；PC 路由 /fabushi/content）
#   分享广场 → 认知广场（权限中心菜单改名；PC 路由 /fabushi/feed）
# 扫描范围: 小程序源码(js/wxml/json/wxss)与 PC 前端源码(src 下的 vue/js/ts/mjs)。
#   不扫 node_modules、miniprogram_npm、docs——docs 另有文档同步(GL-17)，其中原型目录已冻结、不许改。
# 契约自身的测试: scripts/test-check-fabushi-terminology.sh
# 用法: bash scripts/check-fabushi-terminology.sh [源码根目录]   (默认是本仓库)
# 退出码: 0 没有残留 / 1 有残留或被扫的目录不存在(逐处列出 / 说明是哪个目录) / 2 扫描器不可用(fail-closed，不静默通过)
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
RETIRED='内容治理|分享广场'

fail() { echo "FAIL: $*" >&2; exit 1; }
command -v grep >/dev/null 2>&1 || { echo "BLOCKED: 找不到 grep，无法扫描" >&2; exit 2; }
# 两个被扫的目录都必须在: 根目录传错时不能“什么都没扫到”却通过
[ -d "$ROOT/aigicrm-miniprogram" ] || fail "被扫的目录不存在: $ROOT/aigicrm-miniprogram（参数应是仓库根目录）"
[ -d "$ROOT/aigicrm-ui/src" ] || fail "被扫的目录不存在: $ROOT/aigicrm-ui/src（参数应是仓库根目录）"

hits="$(
  { grep -rnE "$RETIRED" "$ROOT/aigicrm-miniprogram" \
      --include='*.js' --include='*.wxml' --include='*.json' --include='*.wxss' \
      --exclude-dir=node_modules --exclude-dir=miniprogram_npm || true
    grep -rnE "$RETIRED" "$ROOT/aigicrm-ui/src" \
      --include='*.vue' --include='*.js' --include='*.ts' --include='*.mjs' --exclude-dir=node_modules || true
  } | sed "s|^$ROOT/||"
)"

if [ -n "$hits" ]; then
  echo "FAIL: 用户看得到的源码里还有已废止的名称（内容治理→内容管理；分享广场→认知广场）:" >&2
  printf '%s\n' "$hits" | sed 's/^/  /' >&2
  exit 1
fi
echo 'PASS: 法布施已废止的名称（内容治理、分享广场）在小程序与 PC 前端源码里没有残留。'
