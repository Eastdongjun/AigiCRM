#!/bin/bash
# AIGICRM daily full backup + 15-day retention.
#
# Backup naming:
#   <YYYYMMDD>-code-aigicrm-source.tar.gz
#   <YYYYMMDD>-database-production-sql.tar.gz
#   <YYYYMMDD>-files-production-uploads.tar.gz
#   <YYYYMMDD>-program-production-deploy.tar.gz
#   <YYYYMMDD>-other-production-metadata.tar.gz
#
# The script is intentionally local-first. It packages the current project source
# and the mirrored production backup tree, then removes dated backup sets older
# than the rolling retention window.
#
# 【备份新鲜度修复 2026-10-07】打包前先做"镜像拉新"：此前 database/files/deploy 三个 tar
# 打包的是 09-生产环境备份 的陈旧镜像（launchd 修复后 10-05/10-06 两晚连续打包了 9-23 的旧库，
# 即"每天在跑但跑的是旧数据"的假新鲜）。现每晚从生产拉当天库 dump（约 2MB）与两个关键部署
# 配置进镜像再打包；拉新失败不阻断备份（降级打包现有镜像并在日志留痕）。
# uploads 大件（3.7G/晚）不经 Mac 中转：服务器 NAS daily 已有新鲜全量，Mac 保留基线镜像即可。
#
# 【2026-10-08 审查修复·基建批（OpenCodeReview 2026-10-07）】
#   IH4: code tar 整目录排除 06-部署与运维/阿里云环境——SSH 私钥 aigicrm.com.pem 此前随包
#        进入每日轮转备份归档（tar -tzf 实证），密钥文件不得入任何备份包。
#   IH5: MANIFEST 增记"服务器 daily 源头日期"并与本地当日比对——此前只看本地 mtime，
#        服务器侧断档 N 天时每晚照拉旧 dump 却显示成"当天"，假新鲜只治了一半。
#   IM5: dump 拉新增加 md5 完整性校验（scp 前远端取码、落盘后本地比对），
#        不符/取不到码一律按拉取失败降级并记日志，不再只靠"文件非空"判断。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_ROOT="$(cd "$SOURCE_ROOT/.." && pwd)"

TODAY="${AIGICRM_BACKUP_DATE:-$(date +%Y%m%d)}"
RETENTION_DAYS="${AIGICRM_BACKUP_RETENTION_DAYS:-15}"
BACKUP_INDEX_DIR="${AIGICRM_BACKUP_INDEX_DIR:-$WORKSPACE_ROOT/08-备份索引}"
PROD_MIRROR_DIR="${AIGICRM_PROD_MIRROR_DIR:-$BACKUP_INDEX_DIR/09-生产环境备份}"
BACKUP_ROOT="${AIGICRM_DAILY_BACKUP_ROOT:-$BACKUP_INDEX_DIR/10-每日全量备份}"
DEST="$BACKUP_ROOT/$TODAY"
MANIFEST="$DEST/MANIFEST.md"
# 【IH5】refresh_prod_mirror 在子 shell 内运行，服务器源头日期经此文件带回主 shell，
# 供下方告警与 MANIFEST freshness 段使用（每晚随 $DEST 整目录过期删除，无残留跨日问题）
MIRROR_SOURCE_DATE_FILE="$DEST/.mirror-source-date"

mkdir -p "$DEST"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

# 【IH5】计算两个 YYYYMMDD 的日差（后者减前者，单位天）；python3 缺席时退回 macOS date
ymd_diff_days() {
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$1" "$2" <<'PY'
from datetime import datetime
import sys
print((datetime.strptime(sys.argv[2], "%Y%m%d") - datetime.strptime(sys.argv[1], "%Y%m%d")).days)
PY
  else
    echo $(( ( $(date -j -f "%Y%m%d" "$2" "+%s") - $(date -j -f "%Y%m%d" "$1" "+%s") ) / 86400 ))
  fi
}

# ---- 镜像拉新（见文件头说明；全部失败均只记日志，不中断备份）----
refresh_prod_mirror() {
  rm -f "$MIRROR_SOURCE_DATE_FILE"  # 【IH5】先清上次运行遗留，MANIFEST 只反映本次拉新结果
  local env_file="${AIGICRM_DEPLOY_ENV:-$HOME/.aigicrm-deploy.env}"
  if [ ! -f "$env_file" ]; then
    log "mirror refresh skipped: 部署 env 不存在($env_file)，将打包现有镜像"
    return 0
  fi
  # 子 shell 内 source，避免部署密钥变量污染本脚本其余部分
  (
    set -a
    # shellcheck disable=SC1090
    . "$env_file"
    set +a
    # PROD_KEY 在部署 env 中通常未显式定义（deploy-lib 同款默认路径兜底）
    local key="${PROD_KEY:-$WORKSPACE_ROOT/06-部署与运维/阿里云环境/aigicrm.com.pem}"
    if [ ! -f "$key" ]; then
      log "mirror refresh skipped: 密钥不存在($key)"
      exit 0
    fi
    local host="${PROD_USER:-root}@${PROD_HOST:?PROD_HOST missing}"
    local opts="-o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=no"
    local latest
    latest="$(ssh -i "$key" $opts "$host" \
      "ls -1 /Aigicrm/files/backup/daily/ 2>/dev/null | grep -E '^[0-9]{8}\$' | sort | tail -1" || true)"
    if [ -z "$latest" ]; then
      log "mirror refresh: 服务器 daily 目录不可达或为空，降级打包现有镜像"
      exit 0
    fi
    # 【IH5】拿到服务器 daily 最新目录即记录源头日期——无论后续 dump 是否拉取成功，
    # "服务器最新备份是哪天"都是识别假新鲜的依据（仅凭本地 mtime 无法发现源头断档）
    printf '%s\n' "$latest" > "$MIRROR_SOURCE_DATE_FILE"
    local dump="/Aigicrm/files/backup/daily/$latest/ycmn-db-$latest.sql"
    mkdir -p "$PROD_MIRROR_DIR/database"
    # 【IM5】scp 前先取远端 md5，落盘后本地比对——传输截断/半包不得只靠"非空"判断放过
    local remote_md5
    remote_md5="$(ssh -i "$key" $opts "$host" "md5sum '$dump' 2>/dev/null | awk '{print \$1}'" || true)"
    local tmp_dump="$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp"
    if scp -i "$key" $opts "$host:$dump" "$tmp_dump" 2>/dev/null \
       && [ -s "$tmp_dump" ]; then
      local local_md5
      local_md5="$(md5 -q "$tmp_dump")"
      if [ -z "$remote_md5" ]; then
        rm -f "$tmp_dump"
        log "mirror refresh: 远端 md5 未取到(ssh 查询失败?)，无法校验完整性，按拉取失败降级打包现有镜像"
      elif [ "$local_md5" != "$remote_md5" ]; then
        rm -f "$tmp_dump"
        log "mirror refresh: dump md5 不符(远端=$remote_md5 本地=$local_md5)，按拉取失败降级打包现有镜像"
      else
        mv -f "$tmp_dump" "$PROD_MIRROR_DIR/database/ycmn-production.sql"
        log "mirror refresh: 库 dump 已拉新并通过 md5 校验(daily/$latest → ycmn-production.sql)"
      fi
    else
      rm -f "$tmp_dump"
      log "mirror refresh: 库 dump 拉取失败，降级打包现有镜像"
    fi
    # 两个关键部署配置小文件顺带对齐（Dockerfile-run/nginx-ssl.conf，服务器为准）
    mkdir -p "$PROD_MIRROR_DIR/deploy"
    for f in Dockerfile-run nginx-ssl.conf; do
      if scp -i "$key" $opts "$host:/root/ycmn/$f" "$PROD_MIRROR_DIR/deploy/$f.tmp" 2>/dev/null \
         && [ -s "$PROD_MIRROR_DIR/deploy/$f.tmp" ]; then
        mv -f "$PROD_MIRROR_DIR/deploy/$f.tmp" "$PROD_MIRROR_DIR/deploy/$f"
      else
        rm -f "$PROD_MIRROR_DIR/deploy/$f.tmp"
      fi
    done
  ) || log "mirror refresh: 子步骤异常，继续按现有镜像打包"
}

archive_if_exists() {
  local output="$1"
  shift
  local inputs=()
  local item
  for item in "$@"; do
    [ -e "$item" ] && inputs+=("$item")
  done
  if [ "${#inputs[@]}" -eq 0 ]; then
    log "skip $output: no source files"
    return 0
  fi
  /usr/bin/tar -czf "$output" "${inputs[@]}"
}

log "creating daily backup in $DEST"

refresh_prod_mirror

# 【IH5】源头新鲜度告警：服务器 daily 源头日期 ≠ 本地当日 ⇒ 服务器侧备份断档，
# 本晚拉到的必是旧 dump（假新鲜）。只告警不阻断——旧 dump 的备份仍有价值，照常打包。
MIRROR_SOURCE_DATE="$(cat "$MIRROR_SOURCE_DATE_FILE" 2>/dev/null || true)"
if [ -n "$MIRROR_SOURCE_DATE" ] && [ "$MIRROR_SOURCE_DATE" != "$TODAY" ]; then
  SOURCE_LAG_DAYS="$(ymd_diff_days "$MIRROR_SOURCE_DATE" "$TODAY")"
  if [ "$SOURCE_LAG_DAYS" -gt 0 ] 2>/dev/null; then
    log "⚠️ 假新鲜告警: 服务器 daily 源头 $MIRROR_SOURCE_DATE 落后本地 $TODAY 共 ${SOURCE_LAG_DAYS} 天——服务器侧备份断档，本包库 dump 非当天数据(不阻断备份)"
  else
    log "⚠️ 服务器 daily 源头日期($MIRROR_SOURCE_DATE) 晚于本地当日($TODAY)，疑似时区/时钟偏差，请人工核对"
  fi
fi

(
  cd "$WORKSPACE_ROOT"
  # 【IH4】06-部署与运维/阿里云环境 整目录排除：aigicrm.com.pem(SSH 私钥)禁止进入备份归档
  # （该目录当前仅含此私钥；若日后新增需备份的文件，改为只排除 *.pem 并重新评估）
  /usr/bin/tar -czf "$DEST/$TODAY-code-aigicrm-source.tar.gz" \
    --exclude='.git' \
    --exclude='**/node_modules' \
    --exclude='**/target' \
    --exclude='**/dist' \
    --exclude='06-部署与运维/阿里云环境' \
    --exclude='08-备份索引' \
    --exclude='.DS_Store' \
    '02-迭代源代码' \
    '05-测试与质量' \
    '06-部署与运维' \
    'AGENTS.md' \
    'AIGICRM.code-workspace' \
    'sonar-project.properties' \
    'sonar-project.properties.example'
)

if [ -d "$PROD_MIRROR_DIR" ]; then
  (
    cd "$PROD_MIRROR_DIR"
    archive_if_exists "$DEST/$TODAY-database-production-sql.tar.gz" database
    archive_if_exists "$DEST/$TODAY-files-production-uploads.tar.gz" uploads images
    archive_if_exists "$DEST/$TODAY-program-production-deploy.tar.gz" deploy
    archive_if_exists "$DEST/$TODAY-other-production-metadata.tar.gz" metadata
  )
else
  log "production mirror not found: $PROD_MIRROR_DIR"
fi

(
  echo "# AIGICRM daily backup manifest - $TODAY"
  echo
  echo "Retention days: $RETENTION_DAYS"
  echo "Backup directory: $DEST"
  echo
  echo "## Archives"
  ls -lh "$DEST"/*.tar.gz 2>/dev/null || true
  echo
  echo "## SHA256"
  shasum -a 256 "$DEST"/*.tar.gz 2>/dev/null || true
  echo
  echo "## Mirror freshness"
  # 【IH5】先报告服务器 daily 源头日期并与本地当日比对——本地 mtime 只能证明"拉过"，
  # 不能证明"拉的是当天"；源头断档时此处必须一眼可见，不能伪装成新鲜。
  if [ -n "${MIRROR_SOURCE_DATE:-}" ]; then
    echo "- 服务器 daily 源头日期: $MIRROR_SOURCE_DATE"
    if [ "$MIRROR_SOURCE_DATE" = "$TODAY" ]; then
      echo "  - ✅ 源头与本地当日一致，拉新对象为当天 dump"
    else
      MANIFEST_LAG="$(ymd_diff_days "$MIRROR_SOURCE_DATE" "$TODAY")"
      if [ "$MANIFEST_LAG" -gt 0 ] 2>/dev/null; then
        echo "  - ⚠️ 源头落后 ${MANIFEST_LAG} 天：服务器侧备份断档，本包库 dump 非当天数据(已 log warning，未阻断备份)"
      else
        echo "  - ⚠️ 源头日期晚于本地当日(时区/时钟偏差?)，请人工核对"
      fi
    fi
  else
    echo "- 服务器 daily 源头日期: 未知(本次拉新未达服务器或未取到 daily 目录，以下 mtime 为既有镜像时间)"
  fi
  echo "- database/ycmn-production.sql: $(stat -f '%Sm' "$PROD_MIRROR_DIR/database/ycmn-production.sql" 2>/dev/null || echo '缺失(拉新失败?)')"
  echo "- deploy/Dockerfile-run: $(stat -f '%Sm' "$PROD_MIRROR_DIR/deploy/Dockerfile-run" 2>/dev/null || echo 缺失)"
  echo "- deploy/nginx-ssl.conf: $(stat -f '%Sm' "$PROD_MIRROR_DIR/deploy/nginx-ssl.conf" 2>/dev/null || echo 缺失)"
) > "$MANIFEST"

if command -v python3 >/dev/null 2>&1; then
  CUTOFF="$(python3 - "$TODAY" "$RETENTION_DAYS" <<'PY'
from datetime import datetime, timedelta
import sys
today = datetime.strptime(sys.argv[1], "%Y%m%d")
retention = int(sys.argv[2])
print((today - timedelta(days=retention - 1)).strftime("%Y%m%d"))
PY
)"
else
  CUTOFF="$(date -v-"$((RETENTION_DAYS - 1))"d +%Y%m%d)"
fi

log "retention cutoff: keep $CUTOFF through $TODAY"

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | while IFS= read -r dir; do
  base="$(basename "$dir")"
  case "$base" in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
      if [ "$base" -lt "$CUTOFF" ]; then
        log "delete expired backup set: $dir"
        find "$dir" -type f -delete
        find "$dir" -depth -type d -empty -exec rmdir {} \;
      fi
      ;;
  esac
done

log "backup complete"
sed -n '1,120p' "$MANIFEST"
