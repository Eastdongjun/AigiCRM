#!/bin/bash
# 前端部署: Mac 构建 → 上传生产 → 切换 NAS dist → 验证
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

UI_DIR="/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/ycmn-ui"
COMMIT=$(git_commit)
echo "=== [1/5] 构建前端 (commit: $COMMIT) ==="
cd "$UI_DIR" && npm run build 2>&1 | grep -E "built in|error" | head -2

echo "=== [2/5] 打包并上传生产 ==="
TARBALL="/tmp/ycmn-dist-$(date +%Y%m%d-%H%M%S).tgz"
tar czf "$TARBALL" -C "$UI_DIR/dist" . 2>/dev/null
scp_prod "$TARBALL" "${PROD_USER}@${PROD_HOST}:/tmp/ycmn-dist-new.tgz"
rm -f "$TARBALL"

echo "=== [3/5] 备份当前 dist 并切换 ==="
ssh_prod "
  TS=\$(date +%Y%m%d-%H%M%S)
  mkdir -p $NAS_BACKUP_DIR/dist-rollback-\$TS
  cp -r $NAS_DIST_DIR/* $NAS_BACKUP_DIR/dist-rollback-\$TS/ 2>/dev/null
  rm -rf $NAS_DIST_DIR/* && tar xzf /tmp/ycmn-dist-new.tgz -C $NAS_DIST_DIR
  echo '新入口: \$(grep -oE \"assets/index-[A-Za-z0-9_-]+\.js\" $NAS_DIST_DIR/index.html)'
  echo \"回滚备份: $NAS_BACKUP_DIR/dist-rollback-\$TS\"
"

echo "=== [4/5] 验证 ==="
sleep 2
curl -sk -o /dev/null -w "公网首页: HTTP %{http_code}\n" https://www.aigicrm.com/
check_health

echo "=== [5/5] 部署记录 ==="
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) frontend deploy commit=$COMMIT\" >> /root/ycmn/deploy.log"
echo "✅ 前端部署完成 (commit: $COMMIT)"
