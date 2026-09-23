#!/bin/bash
# 前端部署: Mac 构建 → 上传生产 → 切换 NAS dist → 验证
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
echo "=== [1/5] 构建前端 (commit: $COMMIT) ==="
cd "$UI_DIR" && npm run build 2>&1 | grep -E "built in|error" | head -2

echo "=== [2/5] 打包并上传生产 ==="
TARBALL="/tmp/${APP_SLUG}-dist-$(date +%Y%m%d-%H%M%S).tgz"
tar czf "$TARBALL" -C "$UI_DIR/dist" . 2>/dev/null
scp_prod "$TARBALL" "${PROD_USER}@${PROD_HOST}:/tmp/${APP_SLUG}-dist-new.tgz"
rm -f "$TARBALL"

echo "=== [3/5] 备份当前 dist 并切换 ==="
ssh_prod "
  TS=\$(date +%Y%m%d-%H%M%S)
  mkdir -p $NAS_BACKUP_DIR/dist-rollback-\$TS
  cp -r $NAS_DIST_DIR/* $NAS_BACKUP_DIR/dist-rollback-\$TS/ 2>/dev/null
  rm -rf $NAS_DIST_DIR/* && tar xzf /tmp/${APP_SLUG}-dist-new.tgz -C $NAS_DIST_DIR
  echo \"新入口: \$(grep -oE \"assets/index-[A-Za-z0-9_-]+\.js\" $NAS_DIST_DIR/index.html)\"
  echo \"回滚备份: $NAS_BACKUP_DIR/dist-rollback-\$TS\"
"

echo "=== [3.4/5] 注入常用页面 chunk prefetch(浏览器解析 HTML 即后台预取,首次点击菜单秒开) ==="
# 在服务器 NAS dist 上注入:chunk 文件名与本地构建一致,部署后修改 index.html
ssh_prod "PYTHONIOENCODING=utf-8 python3 - <<'PYIN'
import os, re
idx = '$NAS_DIST_DIR/index.html'
s = open(idx, encoding='utf-8').read()
if 'rel=\"prefetch\"' in s:
    print('index.html 已含 prefetch,跳过')
else:
    assets = '$NAS_DIST_DIR/assets'
    files = sorted(f for f in os.listdir(assets) if re.match(r'^(customer|incentive|stats|search|statistics|user)-[A-Za-z0-9_-]+\\.js$', f))
    links = ''.join(f'<link rel=\"prefetch\" href=\"/assets/{f}\">' for f in files[:8])
    s = s.replace('</head>', links + '</head>')
    open(idx, 'w', encoding='utf-8').write(s)
    print('prefetch 已注入:', len(files), '个')
PYIN"

echo "=== [3.5/5] 写入版本号(前端版本检测用) ==="
ssh_prod "echo '$(date +%Y%m%d-%H%M%S)' > $NAS_DIST_DIR/version.txt"

echo "=== [4/5] 验证 ==="
sleep 2
curl -sk -o /dev/null -w "公网首页: HTTP %{http_code}\n" https://www.aigicrm.com/
check_health

echo "=== [5/5] 部署记录 ==="
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) frontend deploy commit=$COMMIT\" >> $DEPLOY_DIR/deploy.log"
echo "✅ 前端部署完成 (commit: $COMMIT)"
