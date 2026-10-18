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
# 配置进镜像再打包。
# uploads 大件（3.7G/晚）不经 Mac 中转：服务器 NAS daily 已有新鲜全量，Mac 保留基线镜像即可。
#
# 【2026-10-08 审查修复·基建批（OpenCodeReview 2026-10-07）】
#   IH4: code tar 整目录排除 06-部署与运维/阿里云环境——SSH 私钥 aigicrm.com.pem 此前随包
#        进入每日轮转备份归档（tar -tzf 实证），密钥文件不得入任何备份包。
#   IH5: MANIFEST 增记"服务器 daily 源头日期"并与本地当日比对——此前只看本地 mtime，
#        服务器侧断档 N 天时每晚照拉旧 dump 却显示成"当天"，假新鲜只治了一半。
#   IM5: dump 拉新增加 md5 完整性校验（scp 前远端取码、落盘后本地比对），
#        不符/取不到码一律按拉取失败处理。
#
# 【ops-1 2026-10-17 fail-closed 修复】
#   - 缺部署 env / SSH 密钥 / PROD_HOST / 源目录 / 备份目录不可写：非零退出；
#   - mirror 拉新失败默认非零退出（不再 return 0 后仍打印 complete）；确需降级打包现有镜像时，
#     必须显式 AIGICRM_BACKUP_ALLOW_DEGRADED=1，且 MANIFEST 会标记 degraded；
#   - 每个 tar 产物做 非空 + tar -tzf 可解包 + SHA256 校验；skip 不计成功；
#   - 最终输出实际产物数量/总大小；0 产物或缺少 code/database 必需产物 → 非零退出。
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_ROOT="$(cd "$SOURCE_ROOT/.." && pwd)"

TODAY="${AIGICRM_BACKUP_DATE:-$(date +%Y%m%d)}"
RETENTION_DAYS="${AIGICRM_BACKUP_RETENTION_DAYS:-15}"
BACKUP_INDEX_DIR="${AIGICRM_BACKUP_INDEX_DIR:-$WORKSPACE_ROOT/08-备份索引}"
PROD_MIRROR_DIR="${AIGICRM_PROD_MIRROR_DIR:-$BACKUP_INDEX_DIR/09-生产环境备份}"
BACKUP_ROOT="${AIGICRM_DAILY_BACKUP_ROOT:-$BACKUP_INDEX_DIR/10-每日全量备份}"
DEST="$BACKUP_ROOT/$TODAY"
RUN_START_EPOCH="$(date +%s)"
MANIFEST="$DEST/MANIFEST.md"
SHA_FILE="$DEST/SHA256SUMS"
# 【IH5】refresh_prod_mirror 在子 shell 内运行，服务器源头日期经此文件带回主 shell，
# 供下方告警与 MANIFEST freshness 段使用（每晚随 $DEST 整目录过期删除，无残留跨日问题）
MIRROR_SOURCE_DATE_FILE="$DEST/.mirror-source-date"
MIRROR_DEGRADED_FILE="$DEST/.mirror-degraded"
MIRROR_HARD_FAIL_FILE="$DEST/.mirror-hard-fail"
SKIPPED_FILE="$DEST/.skipped-archives"
MIRROR_DEGRADED=0

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() { log "ERROR: $*"; exit 1; }

case "$TODAY" in
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
  *) die "AIGICRM_BACKUP_DATE 必须是 YYYYMMDD（当前: $TODAY）" ;;
esac
case "$RETENTION_DAYS" in
  ''|*[!0-9]*) die "AIGICRM_BACKUP_RETENTION_DAYS 必须是正整数（当前: $RETENTION_DAYS）" ;;
esac
[ "$RETENTION_DAYS" -ge 1 ] || die "AIGICRM_BACKUP_RETENTION_DAYS 必须 >= 1"

# 源目录必须存在（tar 不会因缺失目录而"成功"）
for d in \
  "$WORKSPACE_ROOT/02-迭代源代码" \
  "$WORKSPACE_ROOT/05-测试与质量" \
  "$WORKSPACE_ROOT/06-部署与运维"; do
  [ -d "$d" ] || die "源目录不存在: $d"
done

mkdir -p "$DEST" || die "无法创建备份目录: $DEST"
[ -w "$DEST" ] || die "备份目录不可写: $DEST"
touch "$DEST/.write-test" && rm -f "$DEST/.write-test" || die "备份目录写入测试失败: $DEST"
rm -f "$SKIPPED_FILE" "$MIRROR_DEGRADED_FILE" "$MIRROR_HARD_FAIL_FILE"
: > "$SHA_FILE"

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

# 拉新失败策略：默认 fail-closed；只有显式 AIGICRM_BACKUP_ALLOW_DEGRADED=1 才允许降级。
mirror_fail() {
  if [ "${AIGICRM_BACKUP_ALLOW_DEGRADED:-0}" = "1" ]; then
    log "WARN: $*（AIGICRM_BACKUP_ALLOW_DEGRADED=1，降级打包现有镜像）"
    printf '%s\n' "$*" >> "$MIRROR_DEGRADED_FILE"
    return 0
  fi
  log "ERROR: $*（如确需降级打包现有镜像，显式设置 AIGICRM_BACKUP_ALLOW_DEGRADED=1）"
  return 1
}

verify_archive() {
  local f="$1"
  [ -s "$f" ] || { log "ERROR: 产物为空: $f"; return 1; }
  if ! /usr/bin/tar -tzf "$f" >/dev/null 2>&1; then
    log "ERROR: 产物无法解包(tar -tzf): $f"
    return 1
  fi
  return 0
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
    log "SKIP $(basename "$output"): 无源文件"
    return 2
  fi
  if ! /usr/bin/tar -czf "$output" "${inputs[@]}"; then
    rm -f "$output"
    log "ERROR: 打包失败: $(basename "$output")"
    return 1
  fi
  if ! verify_archive "$output"; then
    rm -f "$output"
    return 1
  fi
  log "OK $(basename "$output") ($(du -h "$output" | awk '{print $1}'))"
  return 0
}

# ---- 镜像拉新（缺配置/密钥/主机直接失败；网络/拉取失败默认失败，可显式降级）----
refresh_prod_mirror() {
  rm -f "$MIRROR_SOURCE_DATE_FILE"
  local env_file="${AIGICRM_DEPLOY_ENV:-$HOME/.aigicrm-deploy.env}"
  if [ ! -f "$env_file" ] || [ ! -r "$env_file" ]; then
    log "ERROR: 部署 env 不存在或不可读($env_file)，拒绝宣称备份成功"
    printf 'deploy env missing\n' > "$MIRROR_HARD_FAIL_FILE"
    return 1
  fi
  if ! (
    set -Eeuo pipefail
    set -a
    # shellcheck disable=SC1090
    . "$env_file"
    set +a
    local key="${PROD_KEY:-$WORKSPACE_ROOT/06-部署与运维/阿里云环境/aigicrm.com.pem}"
    if [ ! -f "$key" ] || [ ! -r "$key" ]; then
      log "ERROR: 生产 SSH 密钥不存在或不可读($key)"
      printf 'prod ssh key missing\n' > "$MIRROR_HARD_FAIL_FILE"
      exit 1
    fi
    if [ -z "${PROD_HOST:-}" ]; then
      log "ERROR: 部署 env 缺少 PROD_HOST"
      printf 'PROD_HOST missing\n' > "$MIRROR_HARD_FAIL_FILE"
      exit 1
    fi
    local host="${PROD_USER:-root}@${PROD_HOST}"
    local opts="-o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=no"
    local latest
    if ! latest="$(ssh -i "$key" $opts "$host" \
      "ls -1 /Aigicrm/files/backup/daily/ 2>/dev/null | grep -E '^[0-9]{8}\$' | sort | tail -1")"; then
      mirror_fail "ssh 查询服务器 daily 目录失败" || exit 1
      exit 0
    fi
    if [ -z "$latest" ]; then
      mirror_fail "服务器 daily 目录不可达或为空" || exit 1
      exit 0
    fi
    # 【IH5】拿到服务器 daily 最新目录即记录源头日期——无论后续 dump 是否拉取成功，
    # "服务器最新备份是哪天"都是识别假新鲜的依据（仅凭本地 mtime 无法发现源头断档）
    printf '%s\n' "$latest" > "$MIRROR_SOURCE_DATE_FILE"
    local dump="/Aigicrm/files/backup/daily/$latest/ycmn-db-$latest.sql"
    mkdir -p "$PROD_MIRROR_DIR/database"
    # 【IM5】scp 前先取远端 md5，落盘后本地比对——传输截断/半包不得只靠"非空"判断放过
    local remote_md5
    if ! remote_md5="$(ssh -i "$key" $opts "$host" "md5sum '$dump' 2>/dev/null | awk '{print \$1}'")"; then
      mirror_fail "远端 dump md5 查询失败" || exit 1
      exit 0
    fi
    if [ -z "$remote_md5" ]; then
      mirror_fail "远端 dump 不存在或 md5 为空($dump)" || exit 1
      exit 0
    fi
    local tmp_dump="$PROD_MIRROR_DIR/database/ycmn-production.sql.tmp"
    if ! scp -i "$key" $opts "$host:$dump" "$tmp_dump"; then
      rm -f "$tmp_dump"
      mirror_fail "dump scp 拉取失败" || exit 1
      exit 0
    fi
    if [ ! -s "$tmp_dump" ]; then
      rm -f "$tmp_dump"
      mirror_fail "dump 落盘为空" || exit 1
      exit 0
    fi
    local local_md5
    local_md5="$(md5 -q "$tmp_dump")"
    if [ "$local_md5" != "$remote_md5" ]; then
      rm -f "$tmp_dump"
      mirror_fail "dump md5 不符(远端=$remote_md5 本地=$local_md5)" || exit 1
      exit 0
    fi
    mv -f "$tmp_dump" "$PROD_MIRROR_DIR/database/ycmn-production.sql"
    log "mirror refresh: 库 dump 已拉新并通过 md5 校验(daily/$latest → ycmn-production.sql)"
    # 两个关键部署配置小文件顺带对齐（Dockerfile-run/nginx-ssl.conf，服务器为准）。
    # 辅助文件拉取失败只告警（保留既有镜像），不冒充"新鲜"。
    mkdir -p "$PROD_MIRROR_DIR/deploy"
    local f
    for f in Dockerfile-run nginx-ssl.conf; do
      if scp -i "$key" $opts "$host:/root/ycmn/$f" "$PROD_MIRROR_DIR/deploy/$f.tmp" 2>/dev/null \
         && [ -s "$PROD_MIRROR_DIR/deploy/$f.tmp" ]; then
        mv -f "$PROD_MIRROR_DIR/deploy/$f.tmp" "$PROD_MIRROR_DIR/deploy/$f"
      else
        rm -f "$PROD_MIRROR_DIR/deploy/$f.tmp"
        log "WARN: deploy/$f 拉取失败(保留既有镜像)"
      fi
    done
  ); then
    return 1
  fi
  # 降级标记由 mirror_fail 写入 .mirror-degraded，主流程据此在 MANIFEST 标记 DEGRADED。
  return 0
}

log "creating daily backup in $DEST"

if ! refresh_prod_mirror; then
  if [ -s "$MIRROR_HARD_FAIL_FILE" ]; then
    die "备份配置不完整($(cat "$MIRROR_HARD_FAIL_FILE"))：缺部署 env/SSH 密钥/PROD_HOST 一律失败，不可降级"
  fi
  if [ "${AIGICRM_BACKUP_ALLOW_DEGRADED:-0}" = "1" ]; then
    MIRROR_DEGRADED=1
    log "WARN: mirror refresh 失败，按 AIGICRM_BACKUP_ALLOW_DEGRADED=1 降级打包现有镜像"
  else
    die "mirror refresh 失败，未生成任何新备份（拒绝假成功）"
  fi
fi
if [ -s "$MIRROR_DEGRADED_FILE" ]; then
  MIRROR_DEGRADED=1
fi

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

CODE_TAR="$DEST/$TODAY-code-aigicrm-source.tar.gz"
(
  cd "$WORKSPACE_ROOT"
  # 【IH4】06-部署与运维/阿里云环境 整目录排除：aigicrm.com.pem(SSH 私钥)禁止进入备份归档
  # （该目录当前仅含此私钥；若日后新增需备份的文件，改为只排除 *.pem 并重新评估）
  /usr/bin/tar -czf "$CODE_TAR" \
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
verify_archive "$CODE_TAR" || die "源码归档校验失败: $CODE_TAR"

if [ ! -d "$PROD_MIRROR_DIR" ]; then
  die "生产镜像目录不存在: $PROD_MIRROR_DIR"
fi
(
  cd "$PROD_MIRROR_DIR"
  # database 为必需产物；其余缺失只记 SKIP（不计成功），不冒充已备份
  rc=0
  archive_if_exists "$DEST/$TODAY-database-production-sql.tar.gz" database || rc=$?
  if [ "$rc" -eq 2 ]; then
    die "缺少 database 镜像，数据库备份产物未生成"
  elif [ "$rc" -ne 0 ]; then
    die "database 归档失败"
  fi
  archive_if_exists "$DEST/$TODAY-files-production-uploads.tar.gz" uploads images || {
    [ $? -eq 2 ] && echo "files-production-uploads" >> "$SKIPPED_FILE" || die "uploads 归档失败"
  }
  archive_if_exists "$DEST/$TODAY-program-production-deploy.tar.gz" deploy || {
    [ $? -eq 2 ] && echo "program-production-deploy" >> "$SKIPPED_FILE" || die "deploy 归档失败"
  }
  archive_if_exists "$DEST/$TODAY-other-production-metadata.tar.gz" metadata || {
    [ $? -eq 2 ] && echo "other-production-metadata" >> "$SKIPPED_FILE" || die "metadata 归档失败"
  }
)

# ---- 产物总校验：每个 tar 非空 + 可解包 + SHA256；统计实际数量/大小 ----
ARTIFACTS=0
TOTAL_BYTES=0
STALE_ARTIFACTS=0
for f in "$DEST"/*.tar.gz; do
  [ -e "$f" ] || continue
  verify_archive "$f" >/dev/null || die "产物校验失败: $f"
  # 只把本次运行生成的产物计入成功数量；同一天早期残留的旧 tar 不计入
  if [ "$(stat -f%m "$f")" -ge "$RUN_START_EPOCH" ]; then
    ARTIFACTS=$((ARTIFACTS + 1))
    TOTAL_BYTES=$((TOTAL_BYTES + $(stat -f%z "$f")))
  else
    STALE_ARTIFACTS=$((STALE_ARTIFACTS + 1))
  fi
done

CODE_TAR="$DEST/$TODAY-code-aigicrm-source.tar.gz"
DB_TAR="$DEST/$TODAY-database-production-sql.tar.gz"
[ -s "$CODE_TAR" ] || die "缺少必需产物: code"
[ -s "$DB_TAR" ] || die "缺少必需产物: database"
[ "$(stat -f%m "$CODE_TAR")" -ge "$RUN_START_EPOCH" ] || die "code 产物不是本次运行生成(旧文件)"
[ "$(stat -f%m "$DB_TAR")" -ge "$RUN_START_EPOCH" ] || die "database 产物不是本次运行生成(旧文件)"
[ "$ARTIFACTS" -gt 0 ] || die "0 个本次运行生成的有效备份产物，拒绝宣称成功"
: > "$SHA_FILE"
shasum -a 256 "$DEST"/*.tar.gz > "$SHA_FILE" || die "生成 SHA256SUMS 失败"

(
  echo "# AIGICRM daily backup manifest - $TODAY"
  echo
  echo "Retention days: $RETENTION_DAYS"
  echo "Backup directory: $DEST"
  echo "Status: $([ "$MIRROR_DEGRADED" = "1" ] && echo 'DEGRADED（显式允许，未拉新）' || echo 'OK')"
  echo "Artifacts (this run): $ARTIFACTS files, $TOTAL_BYTES bytes"
  echo "Stale artifacts left from earlier run: $STALE_ARTIFACTS"
  echo
  echo "## Archives (each verified: non-empty + tar -tzf)"
  ls -lh "$DEST"/*.tar.gz 2>/dev/null || true
  echo
  echo "## SHA256"
  cat "$SHA_FILE"
  echo
  echo "## Skipped (not counted as success)"
  if [ -s "$SKIPPED_FILE" ]; then cat "$SKIPPED_FILE"; else echo "(none)"; fi
  echo
  echo "## Mirror freshness"
  # 【IH5】先报告服务器 daily 源头日期并与本地当日比对——本地 mtime 只能证明"拉过"，
  # 不能证明"拉的是当天"；源头断档时此处必须一眼可见
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
    echo "- 服务器 daily 源头日期: 未知(本次拉新未达服务器或未取到 daily 目录，mtime 仅代表既有镜像时间)"
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

log "backup complete: artifacts=$ARTIFACTS total_bytes=$TOTAL_BYTES dir_size=$(du -sh "$DEST" | awk '{print $1}')"
sed -n '1,160p' "$MANIFEST"
