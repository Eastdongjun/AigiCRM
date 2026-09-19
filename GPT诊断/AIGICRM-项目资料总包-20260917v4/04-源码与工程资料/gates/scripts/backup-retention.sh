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
