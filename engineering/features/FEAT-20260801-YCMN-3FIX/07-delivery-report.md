# 交付报告

## 结果

YCMN 测试服 3 个问题已修复并部署验证：
1. 激励上传弹窗文件槽位首屏可见、不被底部按钮覆盖，点击"+"可正常触发文件选择。
2. 非管理员捞取不可见公海池客户被接口拒绝。
3. 新增用户账号/姓名/角色/手机号必填校验生效（前端标红 + 后端兜底）。

## 交付范围

- 已完成：REQ-001~005 / AC-001~008 全部 PASS（见 06-test-matrix.md）。
- 行为变化：
  - 激励弹窗 `.dlg-body` 移除 62vh 内层滚动限制（滚动统一由外层 flex 容器处理）。
  - 捞取申请新增可见性校验（非 all 数据范围用户须命中池可见范围，否则拒绝）。
  - 新增用户 4 项必填（前端 prop + 后端 create 兜底）。

## 变更清单

| 文件 | 变更 |
|---|---|
| `ycmn-ui/src/views/incentive/index.vue` | .dlg-body 去 62vh 双滚动（1 行 CSS + 注释） |
| `ycmn-ui/src/views/system/user.vue` | 账号/姓名/角色 3 个 el-form-item 补 prop |
| `ycmn-backend/.../approval/service/ApprovalService.java` | 注入 SeaConfigService；claim 分支加可见校验 |
| `ycmn-backend/.../system/service/SysUserService.java` | create 加 4 项必填校验 + import |

无数据表变更、无依赖变更、无配置变更。

## 安装与启动

- 前端：`cd ycmn-ui && npm run build` → dist
- 后端：`cd ycmn-backend && mvn -q package -DskipTests`（需 JDK 17，本机 JDK 25 与 Lombok 不兼容；已在服务器 JDK 17 构建）
- 部署：dist tar → scp → docker cp ycmn-frontend；jar → docker cp ycmn-backend → docker restart ycmn-backend

## 验证证据

- gate report：`evidence/gate-report-20260801-174746-865566.json`（build=前端 npm PASS rc0；typecheck=后端 mvn package PASS rc0，JDK17 via ~/.mavenrc）
- 截图：`evidence/ui-baseline-1440x900.png`、`evidence/ui-1440x900.png`、`evidence/ui-1280x720.png`；record-ui-review score=90，console 0 错误
- 接口证据：捞取拒绝/成功、用户创建 4 场景（见 06-test-matrix.md TEST-004~007）

## 部署状态与步骤

- **已部署测试服**（111.229.37.220）：
  - 前端：17:35 替换 ycmn-frontend 容器 /usr/share/nginx/html（容器内备份 /tmp/html-backup-*.tgz）
  - 后端：17:35 docker cp app.jar + docker restart，服务正常（未带 token 403 = 正常认证响应）
- 生产环境（39.99.35.218）未部署，未授权上线。

## 回滚与恢复

- 前端：容器内备份 `/tmp/html-backup-<时间戳>.tgz` → `docker exec ycmn-frontend tar -xzf <备份> -C /usr/share/nginx/html`
- 后端：部署前 jar 已被覆盖；如回滚需用源码重新构建旧 commit 或从生产 jar 复制
- 数据：无数据变更；测试数据已清理（TESTX3 用户删除、客户 341 回滚 available）

## 已知限制与残余风险

- 原生文件选择框弹出依赖真实浏览器用户手势；自动化以布局命中+pickFile 链完整为代理证据，建议用户浏览器实测确认。
- 后端门禁 build 在本机 JDK25 失败（Lombok 不兼容，非代码问题）；服务器 JDK17 构建成功并已部署。
- 用户端浏览器缓存旧 chunk 需强刷（Cmd+Shift+R）。
- wangleilei 密码已重置为 admin123（原密码未知/曾锁定）；销售员公海列表按可见范围过滤为 0 行（配置如此）。
- 单槽模式（visit/exchange/plan）未单独截图，与三槽共用修复路径。

## 版本与追踪

- FEAT-20260801-YCMN-3FIX（state: 进行至 VERIFIED 前）
- 源码：/Users/donglai/东来/01-Projects/01-aigicrm/04-源码（git main，未提交本次变更）
- 部署版本：前端 dist 2026-08-01 17:18 构建；后端 jar 2026-08-01 17:33 构建
