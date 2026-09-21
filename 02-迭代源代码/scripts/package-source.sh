#!/usr/bin/env bash
# 源码交付打包(GPT复审 P1-11 修复 2026-09-21): 三分级打包, 杜绝日志/本地私有配置/环境信息混入外发包
# 用法: bash scripts/package-source.sh <dev|internal|external>
#   dev      = 开发协作包: 全量源码+AGENTS.md(本地配置保留, 仅排除构建产物)
#   internal = 内部运维包: dev 基础上排除 env.local/private config/logs/上传目录
#   external = 外部审计包: internal 基础上再排除 deploy env 示例/docker prod/nginx/ssl 与 docs 中的环境地址文档
# 产物输出到 ~/Desktop/AIGICRM-源代码-<date>-<mode>.zip
set -euo pipefail
MODE="${1:-}"
case "$MODE" in dev|internal|external) ;; *) echo "用法: $0 <dev|internal|external>" >&2; exit 2 ;; esac
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HOME/Desktop/AIGICRM-源代码-$(date +%Y%m%d)-${MODE}.zip"

BASE_EX=( -x "*/node_modules/*" "*/target/*" "*/dist/*" "*/test-results/*" "*/uploads/*" "*.DS_Store" "*/.mimosa/*" )
INTERNAL_EX=( "${BASE_EX[@]}"
  -x "*/config/env.local.js" "*private.config.json" "*/logs/*" "*.log" )
EXTERNAL_EX=( "${INTERNAL_EX[@]}"
  -x "*/deploy*.env*" "*aigicrm-deploy.env.example" "*/docker-compose*.yml" "*/nginx/*" "*/ssl/*" )

cd "$ROOT/.."
case "$MODE" in
  dev)      zip -qr "$OUT" "$(basename "$ROOT")" "${BASE_EX[@]}" ;;
  internal) zip -qr "$OUT" "$(basename "$ROOT")" "${INTERNAL_EX[@]}" ;;
  external) zip -qr "$OUT" "$(basename "$ROOT")" "${EXTERNAL_EX[@]}"
            # 外发包附加扫描: 命中环境地址/密钥样式即失败
            if unzip -p "$OUT" | grep -aE "111\.229\.|39\.99\.|MYSQL_ROOT_PASSWORD=|JWT_SECRET=" | head -3 | grep -q .; then
              echo "FAIL: 外发包含环境地址或密钥样式内容" >&2; rm -f "$OUT"; exit 1
            fi ;;
esac
echo "OK: $OUT ($(du -h "$OUT" | cut -f1), $(unzip -l "$OUT" | tail -1 | awk '{print $2}') 文件)"
