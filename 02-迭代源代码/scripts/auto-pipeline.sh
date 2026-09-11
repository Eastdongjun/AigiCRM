#!/bin/bash
# 一键流水线: 编译验证 → git commit/push → 测试服务器部署 → 测试库迁移 → 冒烟测试
#            → [人工按一次回车确认] → 生产库迁移 → 生产部署 → 生产冒烟测试
#
# 只需要在 Mac 上跑这一个命令: ./scripts/auto-pipeline.sh
#
# 生产那一步前会停下来等你按回车 —— 这是唯一保留的人工确认点，
# 因为今天生产环境已经出过一次线上事故，我不会在没有人看过测试环境结果的情况下自动改生产库/发生产版本。
# 除此之外全程自动，不再需要你手动敲任何命令。

set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
source "$ROOT/scripts/deploy-lib.sh"
source "$ROOT/scripts/ensure-java21.sh"

# ── 如果测试服务器上的代码目录不是这个路径，改这一行 ──
TEST_REPO_DIR="${TEST_REPO_DIR:-/home/${TEST_USER}/AigiCRM}"

FAIL=0
step() { echo; echo "════════ $1 ════════"; }
die()  { echo "❌ $1" >&2; exit 1; }

# ============ Phase 1: 编译验证 + git commit/push ============
step "[1/7] 后端编译"
ensure_java21 || die "JDK 17 环境准备失败，流水线终止"
cd "$ROOT/aigicrm-backend"
run_maven clean compile || die "后端编译失败，流水线终止"
cd "$ROOT"

step "[2/7] 前端构建"
(cd "$ROOT/aigicrm-ui" && npm run build) || die "前端构建失败，流水线终止"

step "[3/7] git commit + push"
git add \
  aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/score/service/ScoreService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/sea/service/SeaService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysUserService.java \
  aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysDictController.java \
  aigicrm-ui/src/layout/index.vue \
  aigicrm-ui/src/views/incentive/search.vue \
  sql/migration/20260804/
if ! git diff --cached --quiet; then
  git commit -m "fix: 权限数据范围统一(custom/org)+文件删除越权+学分覆盖+客户转移分享清理+动态菜单渲染"
  git push origin main || die "git push 失败（检查网络/GitHub 凭据），本地已提交，可稍后手动 push"
else
  echo "无新改动可提交，跳过"
fi

# ============ Phase 2: 测试服务器部署 ============
step "[4/7] 测试服务器: git pull + docker 重建"
ssh_test "cd '$TEST_REPO_DIR' && git pull origin main" || die "测试服务器 git pull 失败，请确认 TEST_REPO_DIR='$TEST_REPO_DIR' 是否正确（可用 TEST_REPO_DIR=实际路径 ./scripts/auto-pipeline.sh 覆盖）"
ssh_test "cd '$TEST_REPO_DIR' && JWT_SECRET=\"\${JWT_SECRET:-test-jwt-secret-local-pipeline-$(date +%s)}\" docker compose up -d --build" || die "测试服务器 docker compose 重建失败"
echo "等待容器启动..."; sleep 15

step "[5/7] 测试库迁移"
MYSQL_CT=$(ssh_test "docker ps --format '{{.Names}}'" | grep -i mysql | head -1)
[ -z "$MYSQL_CT" ] && die "未能在测试服务器上自动找到 MySQL 容器名，需要手动执行 SQL 迁移"
echo "检测到测试库容器: $MYSQL_CT"
for sql in fix-permission-rule-org-id.sql fix-menu-dynamic-sidebar.sql; do
  scp_test "sql/migration/20260804/$sql" "${TEST_USER}@${TEST_HOST}:/tmp/$sql" || die "上传 $sql 失败"
  ssh_test "docker exec -i $MYSQL_CT mysql -u"$TEST_DB_ADMIN_USER" -p"$TEST_DB_ADMIN_PASSWORD" --default-character-set=utf8mb4 aigicrm < /tmp/$sql" \
    && echo "✅ $sql 执行完成" || die "$sql 执行失败"
done

step "[6/7] 测试环境冒烟测试"
./scripts/smoke-test.sh test || echo "⚠️  测试环境冒烟测试有失败项，见上方输出——建议人工检查后再继续生产部署"

# ============ Phase 3: 生产（唯一人工确认点）============
step "[7/7] 生产部署确认"
echo "以上测试环境已经跑完。接下来会：在生产库执行同样的 2 个迁移脚本 → 部署后端 → 部署前端 → 生产冒烟测试。"
read -r -p "确认测试环境结果没问题，继续生产部署？(输入 yes 继续): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "已取消生产部署。测试环境已经是最新代码，你可以稍后确认没问题后手动执行："
  echo "  ./scripts/deploy-db.sh sql/migration/20260804/fix-permission-rule-org-id.sql"
  echo "  ./scripts/deploy-db.sh sql/migration/20260804/fix-menu-dynamic-sidebar.sql"
  echo "  ./scripts/deploy-backend.sh && ./scripts/deploy-frontend.sh"
  exit 0
fi

./scripts/deploy-db.sh sql/migration/20260804/fix-permission-rule-org-id.sql || die "生产库迁移1失败"
./scripts/deploy-db.sh sql/migration/20260804/fix-menu-dynamic-sidebar.sql || die "生产库迁移2失败"
./scripts/deploy-backend.sh || die "生产后端部署失败"
./scripts/deploy-frontend.sh || die "生产前端部署失败"
./scripts/smoke-test.sh prod

echo
echo "✅ 全流程完成：本地编译验证 → git push → 测试部署+迁移+冒烟测试 → 生产迁移+部署+冒烟测试"
