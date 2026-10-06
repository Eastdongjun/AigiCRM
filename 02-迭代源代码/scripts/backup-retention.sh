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

mkdir -p "$DEST"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

# ---- 镜像拉新（见文件头说明；全部失败均只记日志，不中断备份）----
refresh_prod_mirror() {
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
    local dump="/Aigicrm/files/backup/daily/$latest/ycmn-db-$latest.sql"
    mkdir -p "$PROD_MIRROR_DIR/database"
    if scp -i "$key" $opts "$host:$dump" "$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp" 2>/dev/null \
       && [ -s "$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp" ]; then
      mv -f "$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp" "$PROD_MIRROR_DIR/database/ycmn-production.sql"
      log "mirror refresh: 库 dump 已拉新(daily/$latest → ycmn-production.sql)"
    else
      rm -f "$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp"
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

(
  cd "$WORKSPACE_ROOT"
  /usr/bin/tar -czf "$DEST/$TODAY-code-aigicrm-source.tar.gz" \
    --exclude='.git' \
    --exclude='**/node_modules' \
    --exclude='**/target' \
    --exclude='**/dist' \
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
