#!/bin/bash
# 【待建B·转正立项 2026-11-22】部署后内容级核验——K-ENG-FE-001 事故补位。
#
# 事故复盘: deploy-frontend.sh 只验 HTTP 200/入口可达, 不验"修复真的在线上"——
# 本次 polyfill 修复部署后靠人工 curl 比对线上 chunk 标记才确认落位。本脚本把该动作固化:
#   线上 assets/* 必须与本地 dist/assets/* 逐文件 sha256 完全一致(严格);
#   index.html / version.txt 为宽松项(部署时注入 prefetch 与时间戳, 仅报告差异, 见
#   deploy-frontend.sh [2/6])。
# 拉取用 curl(系统信任链实测可达; 本机 python urllib CA 链对该站点报自签, 不采用)。
# 用法: bash scripts/verify-deployed-content.sh [--self-test]
#   AIGI_BASE=https://www.aigicrm.com(默认) AIGI_DIST_DIR=aigicrm-ui/dist(默认)
# 接入: deploy-frontend.sh 尾部(G6 内容级核验档); 也可部署后手工跑。
# 退出码: 0=一致; 1=存在不一致(资产不一致=必须人工裁决是否回滚, 备份目录见部署输出)。
# --self-test: 篡改 /tmp 本地副本一字节 → 与线上比对必须红(检查器负例自证, 门禁 H10)。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="${AIGI_BASE:-https://www.aigicrm.com}"
DIST="${AIGI_DIST_DIR:-$SCRIPT_DIR/../aigicrm-ui/dist}"

run_compare() {  # $1=dist 目录; 输出 BAD 行, 返回不一致数
  local d="$1" out bad n
  out=$(cd "$d/assets" && ls | grep -v '^\.' | xargs -P 8 -I{} sh -c \
    'L=$(shasum -a 256 "{}" | cut -d" " -f1); R=$(curl -sf -m 20 "'"$BASE"'/assets/{}" | shasum -a 256 | cut -d" " -f1); [ -n "$L" ] && [ "$L" = "$R" ] || echo "BAD {}"' 2>/dev/null)
  bad=$(printf '%s' "$out" | grep -c '^BAD ' || true)
  n=$(cd "$d/assets" && ls | grep -cv '^\.' || true)
  echo "assets: $((n - bad))/$n 一致" $([ "${bad:-0}" -gt 0 ] && echo "(不一致 $bad:)" ; printf '%s\n' "$out" | grep '^BAD ' | sed 's/^BAD /  ✗ /' | head -10)
  echo "$bad"
}

if [ "${1:-}" = "--self-test" ]; then
  NEG=/tmp/aigi-content-neg-dist
  rm -rf "$NEG" && cp -R "$DIST" "$NEG"
  VICTIM=$(ls "$NEG"/assets/*.js | head -1)
  printf 'X' >> "$VICTIM"
  echo "self-test: 已篡改 $(basename "$VICTIM"), 期望判红"
  RES=$(run_compare "$NEG"); BADN=$(printf '%s\n' "$RES" | tail -1)
  echo "$RES" | head -4
  if [ "${BADN:-0}" -ge 1 ]; then
    echo "PASS: --self-test 负例自证——篡改被正确判红(检查器有效)"; exit 0
  else
    echo "FAIL: --self-test 篡改副本未被识别, 检查器失效!"; exit 1
  fi
fi

echo "=== G6 内容级核验: $BASE vs $DIST (assets 逐文件 sha256) ==="
RES=$(run_compare "$DIST"); BADN=$(printf '%s\n' "$RES" | tail -1)
printf '%s\n' "$RES" | sed '$d'
# 宽松项: index.html / version.txt(部署时注入 prefetch/时间戳) —— 仅报告
for name in index.html version.txt; do
  [ -f "$DIST/$name" ] || continue
  L=$(shasum -a 256 "$DIST/$name" | cut -d' ' -f1)
  R=$(curl -sf -m 20 "$BASE/$name" | shasum -a 256 | cut -d' ' -f1)
  if [ -n "$R" ] && [ "$L" = "$R" ]; then echo "INFO: /$name 一致"
  else echo "INFO: /$name 差异(部署注入 prefetch/时间戳所致, 人工抽查入口引用即可)"; fi
done
if [ "${BADN:-1}" = "0" ]; then echo "==> 内容级核验: PASS"; exit 0
else echo "==> 内容级核验: FAIL——资产不一致, 按门禁 G6 人工裁决回滚(备份见部署输出)"; exit 1; fi