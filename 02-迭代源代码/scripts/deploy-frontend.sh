#!/bin/bash
# 前端部署: Mac 构建 → 上传生产 → 暂存并校验 → 备份旧 dist → 原子切换入口 → 验证
#
# 【ops-2 2026-10-17 fail-closed 修复】
#   原流程 `rm -rf $NAS_DIST_DIR/*` 先删线上再解包：上传包损坏/解包失败时线上直接空白。
#   现改为：上传 → 同盘暂存目录解包并校验 index.html + 入口资源 → 备份旧 dist（失败即停）
#   → 复制资源（旧 index 仍在线）→ 最后用同盘 mv 原子替换 index.html/version.txt。
#   任何一步失败由远端 ERR trap 恢复旧入口并返回非零；HTTP/健康检查失败自动回滚入口。
#
# 说明：生产 nginx 以 bind mount 挂载 NAS_DIST_DIR，目录 inode 被容器固定，
# 不能对目录本身做 mv 交换（容器仍指向旧 inode）。因此"原子切换"落在入口文件
# index.html（mv 同文件系统 rename），资源先就位、入口最后替换，用户永远看到完整版本。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

# 前端源码目录（2026-08-15 修正：仓库 08-14 重组后源码在 02-迭代源代码/ 下，旧路径 04-源码/aigicrm-ui 已废弃）
UI_DIR="${UI_DIR:-$(cd "$SCRIPT_DIR/../aigicrm-ui" && pwd)}"
COMMIT=$(git_commit)
# 【2026-09-12】生产环境前置硬门禁：env 若指向"改名后目标态"而生产未改名，
# 本脚本会**静默空转**（见 deploy-lib.sh 中 assert_prod_env_pinned 的说明）。
# 必须在任何远端动作之前 fail-closed。
assert_prod_env_pinned
check_deploy_arch

TS="$(date +%Y%m%d-%H%M%S)"
TARBALL="/tmp/${APP_SLUG}-dist-${TS}.tgz"
REMOTE_TGZ="/tmp/${APP_SLUG}-dist-${TS}.tgz"
BACKUP_DIR="${NAS_BACKUP_DIR}/dist-rollback-${TS}"
STAGE="${NAS_DIST_DIR%/}.staging-${TS}"

cleanup_local() { rm -f "$TARBALL"; }
trap cleanup_local EXIT

echo "=== [1/6] 构建前端 (commit: $COMMIT) ==="
cd "$UI_DIR" && npm run build 2>&1 | grep -E "built in|error" | head -2
[ -s "$UI_DIR/dist/index.html" ] || { echo "❌ 构建产物缺少 dist/index.html" >&2; exit 1; }

echo "=== [2/6] 注入页面 prefetch（在构建产物上完成，保证上传内容=校验内容） ==="
python3 - "$UI_DIR/dist" <<'PY'
import os, re, sys
dist = sys.argv[1]
idx = os.path.join(dist, 'index.html')
s = open(idx, encoding='utf-8').read()
if 'rel="prefetch"' in s:
    print('index.html 已含 prefetch,跳过')
else:
    assets = os.path.join(dist, 'assets')
    files = sorted(f for f in os.listdir(assets) if re.match(r'^(customer|incentive|stats|search|statistics|user)-[A-Za-z0-9_-]+\.js$', f))
    links = ''.join(f'<link rel="prefetch" href="/assets/{f}">' for f in files[:8])
    if '</head>' not in s:
        raise SystemExit('index.html 缺少 </head>')
    s = s.replace('</head>', links + '</head>')
    open(idx, 'w', encoding='utf-8').write(s)
    print('prefetch 已注入:', len(files), '个')
PY

ENTRY="$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' "$UI_DIR/dist/index.html" | head -1)"
[ -n "$ENTRY" ] || { echo "❌ 构建产物 index.html 未引用 assets/index-*.js" >&2; exit 1; }
[ -s "$UI_DIR/dist/$ENTRY" ] || { echo "❌ 入口资源不存在: dist/$ENTRY" >&2; exit 1; }
printf '%s\n' "$TS" > "$UI_DIR/dist/version.txt"
INDEX_SHA="$(shasum -a 256 "$UI_DIR/dist/index.html" | awk '{print $1}')"
echo "入口: $ENTRY (sha256=$INDEX_SHA)"

echo "=== [3/6] 打包并上传生产（先落 /tmp，不解包到线上） ==="
tar czf "$TARBALL" -C "$UI_DIR/dist" .
scp_prod "$TARBALL" "${PROD_USER}@${PROD_HOST}:$REMOTE_TGZ"

echo "=== [4/6] 同盘暂存 + 校验 + 备份旧 dist + 原子切换 ==="
REMOTE_OUT="$(
  {
    echo 'set -Eeuo pipefail'
    printf 'APP_SLUG=%q\n' "$APP_SLUG"
    printf 'NAS_DIST_DIR=%q\n' "$NAS_DIST_DIR"
    printf 'NAS_BACKUP_DIR=%q\n' "$NAS_BACKUP_DIR"
    printf 'REMOTE_TGZ=%q\n' "$REMOTE_TGZ"
    printf 'STAGE=%q\n' "$STAGE"
    printf 'BACKUP_DIR=%q\n' "$BACKUP_DIR"
    printf 'TS=%q\n' "$TS"
    printf 'INDEX_SHA=%q\n' "$INDEX_SHA"
    printf 'ALLOW_FIRST=%q\n' "${AIGICRM_ALLOW_FIRST_FRONTEND_DEPLOY:-0}"
    cat <<'REMOTE'
SWITCHED=0
cleanup() { rm -rf "$STAGE"; rm -f "$REMOTE_TGZ"; }
restore_entry() {
  local name f
  for name in index.html version.txt; do
    if [ -s "$BACKUP_DIR/$name" ]; then
      f="$NAS_DIST_DIR/.$name.rollback.$TS"
      cp -a "$BACKUP_DIR/$name" "$f"
      mv -f "$f" "$NAS_DIST_DIR/$name"
    fi
  done
}
on_error() {
  rc=$?
  trap - ERR
  if [ "$SWITCHED" = "1" ] && [ -s "$BACKUP_DIR/index.html" ]; then
    echo "❌ 切换后步骤失败(退出码 $rc)，恢复旧入口 ..." >&2
    restore_entry || echo "❌ 旧入口恢复失败，请人工使用 $BACKUP_DIR 回滚" >&2
  else
    echo "❌ 切换前步骤失败(退出码 $rc)，旧入口未改动" >&2
  fi
  exit "$rc"
}
trap cleanup EXIT
trap on_error ERR

[ -s "$REMOTE_TGZ" ] || { echo "❌ 远端上传包不存在/为空: $REMOTE_TGZ" >&2; exit 1; }
mkdir -p "$STAGE"
tar xzf "$REMOTE_TGZ" -C "$STAGE"
[ -s "$STAGE/index.html" ] || { echo "❌ 暂存包缺少 index.html" >&2; exit 1; }
ENTRY=$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' "$STAGE/index.html" | head -1)
[ -n "$ENTRY" ] || { echo "❌ 暂存 index.html 未引用 assets/index-*.js" >&2; exit 1; }
[ -s "$STAGE/$ENTRY" ] || { echo "❌ 暂存包缺少入口资源 $ENTRY" >&2; exit 1; }
ACTUAL_SHA=$(sha256sum "$STAGE/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
[ "$ACTUAL_SHA" = "$INDEX_SHA" ] || { echo "❌ 暂存 index.html sha256 不符(期望=$INDEX_SHA 实际=$ACTUAL_SHA)" >&2; exit 1; }

# 备份旧 dist：cp 失败/校验不符必须中止（原实现 2>/dev/null 吞掉并继续删线上）
if [ -s "$NAS_DIST_DIR/index.html" ]; then
  mkdir -p "$BACKUP_DIR"
  cp -a "$NAS_DIST_DIR/." "$BACKUP_DIR/"
  [ -s "$BACKUP_DIR/index.html" ] || { echo "❌ 旧 dist 备份缺少 index.html" >&2; exit 1; }
  OLD_SHA=$(sha256sum "$NAS_DIST_DIR/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
  BAK_SHA=$(sha256sum "$BACKUP_DIR/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
  [ "$OLD_SHA" = "$BAK_SHA" ] || { echo "❌ 旧 dist 备份校验不符(old=$OLD_SHA backup=$BAK_SHA)" >&2; exit 1; }
  echo "旧 dist 已备份: $BACKUP_DIR"
else
  if [ "$ALLOW_FIRST" != "1" ]; then
    echo "❌ 线上 dist 无 index.html；若确属首次部署，请显式设置 AIGICRM_ALLOW_FIRST_FRONTEND_DEPLOY=1" >&2
    exit 1
  fi
  echo "⚠️ 首次部署：无旧 dist 可备份（AIGICRM_ALLOW_FIRST_FRONTEND_DEPLOY=1）"
fi

# 资源先就位（旧 index 仍在线，指向的旧资源未被删除）；入口文件最后 mv 原子替换
mkdir -p "$NAS_DIST_DIR"
tar -C "$STAGE" --exclude='./index.html' --exclude='./version.txt' -cf - . | tar -C "$NAS_DIST_DIR" -xf -
mv -f "$STAGE/index.html" "$NAS_DIST_DIR/.index.html.new.$TS"
mv -f "$NAS_DIST_DIR/.index.html.new.$TS" "$NAS_DIST_DIR/index.html"
SWITCHED=1
if [ -s "$STAGE/version.txt" ]; then
  cp -a "$STAGE/version.txt" "$NAS_DIST_DIR/.version.txt.new.$TS"
  mv -f "$NAS_DIST_DIR/.version.txt.new.$TS" "$NAS_DIST_DIR/version.txt"
fi
LIVE_SHA=$(sha256sum "$NAS_DIST_DIR/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
[ "$LIVE_SHA" = "$INDEX_SHA" ] || { echo "❌ 线上 index.html 与构建产物 sha256 不符(期望=$INDEX_SHA 实际=$LIVE_SHA)" >&2; exit 1; }
echo "LIVE_INDEX_SHA=$LIVE_SHA"
echo "BACKUP_DIR=$BACKUP_DIR"
echo "ENTRY=$ENTRY"
REMOTE
  } | ssh_prod_once "bash -s"
)"
printf '%s\n' "$REMOTE_OUT"
LIVE_SHA="$(printf '%s\n' "$REMOTE_OUT" | sed -n 's/^LIVE_INDEX_SHA=//p' | tail -1)"
[ "$LIVE_SHA" = "$INDEX_SHA" ] || { echo "❌ 远端回传 index sha256 与期望不符" >&2; exit 1; }

echo "=== [5/6] 公网验证（HTTP 200 / body 含 app 根 / 入口资源 200 / 入口 hash） ==="
BODY_FILE="$(mktemp /tmp/aigicrm-frontend-check.XXXXXX)"
trap 'rm -f "$BODY_FILE"; cleanup_local' EXIT
CODE="$(curl -sk --connect-timeout 10 --max-time 30 -H 'Cache-Control: no-cache' \
  -o "$BODY_FILE" -w '%{http_code}' "https://www.aigicrm.com/?deploy=$TS")"
if [ "$CODE" != "200" ] \
   || ! grep -q 'id="app"' "$BODY_FILE" \
   || ! grep -Fq "$ENTRY" "$BODY_FILE" \
   || [ "$(shasum -a 256 "$BODY_FILE" | awk '{print $1}')" != "$INDEX_SHA" ]; then
  echo "❌ 公网首页校验失败 (HTTP $CODE, entry=$ENTRY, sha期望=$INDEX_SHA 实际=$(shasum -a 256 "$BODY_FILE" | awk '{print $1}'))" >&2
  echo "   正在恢复旧入口 ..." >&2
  {
    echo 'set -euo pipefail'
    printf 'NAS_DIST_DIR=%q\n' "$NAS_DIST_DIR"
    printf 'BACKUP_DIR=%q\n' "$BACKUP_DIR"
    printf 'TS=%q\n' "$TS"
    cat <<'REMOTE_RESTORE'
for name in index.html version.txt; do
  if [ -s "$BACKUP_DIR/$name" ]; then
    cp -a "$BACKUP_DIR/$name" "$NAS_DIST_DIR/.$name.rollback.$TS"
    mv -f "$NAS_DIST_DIR/.$name.rollback.$TS" "$NAS_DIST_DIR/$name"
  fi
done
echo "↩ 已恢复旧入口"
REMOTE_RESTORE
  } | ssh_prod_once "bash -s" || echo "❌ 旧入口恢复失败，请人工使用 $BACKUP_DIR 回滚" >&2
  exit 1
fi
ENTRY_CODE="$(curl -sk --connect-timeout 10 --max-time 30 -o /dev/null -w '%{http_code}' \
  "https://www.aigicrm.com/$ENTRY")"
if [ "$ENTRY_CODE" != "200" ]; then
  echo "❌ 入口资源 $ENTRY 返回 HTTP $ENTRY_CODE，正在恢复旧入口 ..." >&2
  {
    echo 'set -euo pipefail'
    printf 'NAS_DIST_DIR=%q\n' "$NAS_DIST_DIR"
    printf 'BACKUP_DIR=%q\n' "$BACKUP_DIR"
    printf 'TS=%q\n' "$TS"
    cat <<'REMOTE_RESTORE'
for name in index.html version.txt; do
  if [ -s "$BACKUP_DIR/$name" ]; then
    cp -a "$BACKUP_DIR/$name" "$NAS_DIST_DIR/.$name.rollback.$TS"
    mv -f "$NAS_DIST_DIR/.$name.rollback.$TS" "$NAS_DIST_DIR/$name"
  fi
done
echo "↩ 已恢复旧入口"
REMOTE_RESTORE
  } | ssh_prod_once "bash -s" || echo "❌ 旧入口恢复失败，请人工使用 $BACKUP_DIR 回滚" >&2
  exit 1
fi
if ! check_health_retry; then
  echo "❌ 健康检查失败，正在恢复旧入口 ..." >&2
  {
    echo 'set -euo pipefail'
    printf 'NAS_DIST_DIR=%q\n' "$NAS_DIST_DIR"
    printf 'BACKUP_DIR=%q\n' "$BACKUP_DIR"
    printf 'TS=%q\n' "$TS"
    cat <<'REMOTE_RESTORE'
for name in index.html version.txt; do
  if [ -s "$BACKUP_DIR/$name" ]; then
    cp -a "$BACKUP_DIR/$name" "$NAS_DIST_DIR/.$name.rollback.$TS"
    mv -f "$NAS_DIST_DIR/.$name.rollback.$TS" "$NAS_DIST_DIR/$name"
  fi
done
echo "↩ 已恢复旧入口"
REMOTE_RESTORE
  } | ssh_prod_once "bash -s" || echo "❌ 旧入口恢复失败，请人工使用 $BACKUP_DIR 回滚" >&2
  exit 1
fi
echo "✅ 公网首页/入口资源/健康检查通过"

echo "=== [6/6] 部署记录 ==="
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) frontend deploy commit=$COMMIT entry=$ENTRY\" >> $DEPLOY_DIR/deploy.log"
echo "✅ 前端部署完成 (commit: $COMMIT, backup: $BACKUP_DIR)"
