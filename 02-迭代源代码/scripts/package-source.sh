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
  # 尾星覆盖 .disabled-<date> 历史禁用档; 不匹配 env.local.example.js(无 env.local.js 子串, 模板须保留)
  -x "*env.local.js*" "*private.config.json" "*/logs/*" "*.log" )
# 外发档另排除运维/运行手册类文档(实测载体: docs/ 与 MIGRATION-LINEAGE 含内网地址与部署细节)
# 外发档另排除: 运维/运行手册文档、部署与工程脚本(实测携带环境地址/部署细节)、本地环境档
EXTERNAL_EX=( "${INTERNAL_EX[@]}"
  -x "*/deploy*.env*" "*aigicrm-deploy.env.example" "*/docker-compose*.yml" "*/nginx/*" "*/ssl/*"
  # 2026-09-22 修正: zip -x 的 */X 单层星不跨目录, MIGRATION-LINEAGE 实居 db/migration/ 深层,
  # 旧模式 */MIGRATION-LINEAGE.md 对包内 02-迭代源代码/.../db/migration/ 不生效 → 改无斜杠全层匹配;
  # 同理 deploy/ 工程目录(环境口径)整目录排除。
  # config 排除精确化(GPT外审 AUD-P1-002): 只排 env.local.js(本机覆盖档,含环境地址),
  # 保留 env.js(demo 默认档,apiBaseUrl 空,小程序编译必需)与 *.example.js 模板 —— 旧模式 */config/env*.js 连坐全排除致包内小程序不可编译
  -x "*/config/env.local.js" "*/docs/*" "*MIGRATION-LINEAGE.md" "*/scripts/*" "*/deploy/*"
  -x "*/engineering/*" "*/FROZEN-SHA256.txt" )

cd "$ROOT/.."
case "$MODE" in
  dev)      zip -qr "$OUT" "$(basename "$ROOT")" "${BASE_EX[@]}" ;;
  internal) zip -qr "$OUT" "$(basename "$ROOT")" "${INTERNAL_EX[@]}" ;;
  external) zip -qr "$OUT" "$(basename "$ROOT")" "${EXTERNAL_EX[@]}"
            # GPT外审 AUD-P1-012/CI-P1-010: CI workflow 居仓库根 .github/(打包根之外)。
            # 保留原路径加入包内(-j 平铺会让外审误判"不在 GitHub Actions 识别位置", 三轮实证)
            ( cd "$ROOT/.." && zip -q "$OUT" .github/workflows/ci.yml )
            # GPT外审 AUD-P2-001: 附 manifest 明示"包含/不包含",避免误当可直接部署的完整包
            cat > /tmp/pkg-manifest.md <<'MANIFEST'
# 本包内容边界(external 脱敏档)

包含: 后端/前端/小程序完整源码、Flyway 迁移 SQL、CLAUDE.md/AGENTS.md、根级 CI workflow(ci.yml)、小程序 config 模板(env.js demo 默认档+example)。
不包含(有意排除,非缺失): docs/ 运维与审计文档、scripts/ 部署与门禁脚本、engineering/ 工程证据(含 SBOM)、deploy/ 部署编排、docker-compose、nginx/ssl、env.local.js(本机环境档)。
用途: 源码级外部审查。不可直接部署;SBOM/部署/备份证据在完整内部仓库(deploy/cleanroom 与 engineering/evidence)。
MANIFEST
            zip -qj "$OUT" /tmp/pkg-manifest.md && rm -f /tmp/pkg-manifest.md
            # 外发包附加扫描(含上述追加物): 命中环境地址/密钥样式即失败
            if unzip -p "$OUT" | grep -aE "111\.229\.|39\.99\.|MYSQL_ROOT_PASSWORD=|JWT_SECRET=" | head -3 | grep -q .; then
              echo "FAIL: 外发包含环境地址或密钥样式内容" >&2; rm -f "$OUT"; exit 1
            fi ;;
esac
echo "OK: $OUT ($(du -h "$OUT" | cut -f1), $(unzip -l "$OUT" | tail -1 | awk '{print $2}') 文件)"
# external 断言(GPT外审 AUD-P1-002 回归): 小程序 config 模板与 ci.yml 必须在包内
# grep 须加 -a: unzip -l 的中文路径含非 ASCII 字节, 不加会被判二进制致 -c 输出空、断言静默失效(2026-09-22 实证)
if [ "$MODE" = "external" ]; then
  ENVN=$(unzip -l "$OUT" | grep -ac "config/env")
  CIN=$(unzip -l "$OUT" | grep -ac "ci.yml")
  if [ "${ENVN:-0}" -lt 3 ] || [ "${CIN:-0}" -lt 1 ]; then
    echo "FAIL: external 断言未过(env 模板=${ENVN:-0}<3 或 ci.yml=${CIN:-0}<1)" >&2; rm -f "$OUT"; exit 1
  fi
  echo "assert: config/env=$ENVN, ci.yml=$CIN"
fi
