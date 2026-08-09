#!/bin/bash
# 一键: 编译验证 → git commit → git push（C3 2026-08-09: ROOT 去硬编码，按脚本位置推导）
# 只做本地/GitHub 操作，不碰测试服务器和生产服务器。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
source "$ROOT/scripts/ensure-java17.sh"

echo "=== [1/4] 后端编译 (mvn clean compile) ==="
ensure_java17 || exit 1
cd "$ROOT/ycmn-backend"
run_maven clean compile

echo "=== [2/4] 前端构建 (npm run build) ==="
cd "$ROOT/ycmn-ui"
npm run build

echo "=== [3/4] git add + commit ==="
cd "$ROOT"
git add \
  ycmn-backend/src/main/java/com/ycmn/module/auth/service/AuthService.java \
  ycmn-backend/src/main/java/com/ycmn/module/customer/service/CustomerService.java \
  ycmn-backend/src/main/java/com/ycmn/module/file/service/FileService.java \
  ycmn-backend/src/main/java/com/ycmn/module/incentive/service/IncentiveService.java \
  ycmn-backend/src/main/java/com/ycmn/module/score/service/ScoreService.java \
  ycmn-backend/src/main/java/com/ycmn/module/sea/service/SeaService.java \
  ycmn-backend/src/main/java/com/ycmn/module/statistics/service/StatisticsQueryService.java \
  ycmn-backend/src/main/java/com/ycmn/module/system/service/SysUserService.java \
  ycmn-backend/src/main/java/com/ycmn/module/system/controller/SysDictController.java \
  ycmn-ui/src/layout/index.vue \
  ycmn-ui/src/views/incentive/search.vue \
  sql/migration/20260804/

git status --short

git commit -m "fix: 权限数据范围统一(custom/org)+文件删除越权+学分覆盖+客户转移分享清理+动态菜单渲染

- CustomerService/IncentiveService: custom/org/org_and_children 数据范围改为基于 sys_dept + managedOrgIds 解析，替换从未回填的 sys_user.org_id
- FileService.deleteFile: 修复无权限校验的越权删除(IDOR)
- ScoreService.batchSave: 物理删除全期改为增量 upsert，避免误删未提交人员的历史学分
- SysUserService.transferAllCustomers / SeaService: 客户易主(转移/进公海/捞取)后清理 biz_customer_share，避免旧分享人长期保留访问权限
- AuthService.buildMenuTree: 过滤按钮权限点(menu_type=3)与隐藏路由(visible=0)，避免污染侧边栏
- layout/index.vue: 侧边栏改为基于 sys_menu 动态菜单树渲染，替代硬编码 hasPerm 判断
- sql/migration/20260804: 修复 sys_permission_rule 无效字段 + sys_menu 补全4个新模块菜单项
- SysDictController: 字典数据读接口(listData/listDataByTypeId)从 system:config:view 降级为 isAuthenticated——修复销售员/行政等角色打开新增客户等7+业务页面时因缺该管理员权限而403'没有访问权限'的生产报错"

echo "=== [4/4] git push ==="
git push origin main

echo "✅ 完成。commit: $(git rev-parse --short HEAD)"
echo "下一步（需手动，涉及生产环境，建议人工确认再做）："
echo "  1) SSH 到测试服务器拉取最新代码并重建 docker 镜像"
echo "  2) 在测试库执行: mysql < sql/migration/20260804/fix-permission-rule-org-id.sql"
echo "                    mysql < sql/migration/20260804/fix-menu-dynamic-sidebar.sql"
echo "  3) 测试服务器上用不同角色账号验证菜单/权限/数据范围"
echo "  4) 确认无误后: ./scripts/deploy-db.sh sql/migration/20260804/fix-permission-rule-org-id.sql"
echo "                 ./scripts/deploy-db.sh sql/migration/20260804/fix-menu-dynamic-sidebar.sql"
echo "                 ./scripts/deploy-backend.sh"
echo "                 ./scripts/deploy-frontend.sh"
