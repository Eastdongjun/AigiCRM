# 菜单与数据字典改造实施记录（2026-11-16）

## 1. 已落地决策

- 新模块：**流程协同**
- 我的任务：**落库为真实菜单** `/task`，不再前端注入
- 流程协同顺序：**我的任务 → 提醒管理 → 审批管理**
- PC 顶层顺序：首页 → 业务核心 → 流程协同 → 学分管理 → **法布施 → 统计分析** → 系统管理
- MP tabBar：**文件 → 法布施**；文件管理页移入应用中心
- MP 应用中心：客户经营 → 文件管理 → 四项激励 → 法布施 → 学分管理 → 统计分析 → 流程协同
- MP 流程协同：我的任务 → 提醒管理 → 审批管理 → 公海池
- 数据字典：固定模块枚举；空模块保留下拉
- **迁移不硬编码角色码**：任务页绑定从现有 `task:view` 绑定复制，组绑定从子菜单绑定反推

## 2. 数据库迁移

### V2026.11.16.4__menu_workflow_group.sql

- 新建流程协同组（`/workflow-group`, sort=20）
- 顶层重排：0/10/20/30/40/50/60
- 提醒/审批改挂流程协同，sort=2/3
- 新建 `/task` 页面菜单（`task:view`, `task/index`）
- 复制所有现有 `task:view` 绑定到新页面；`task:manage` 挂到页面下；删除旧 task:view 行，保证全库仅 1 个
- 流程协同组按 `task_page/reminder/approval` 子菜单绑定反推，不写角色码

### V2026.11.16.5__dict_type_module.sql

- `sys_dict_type.module_code VARCHAR(50) NOT NULL DEFAULT 'common'`
- 回填 17 个类型：
  - customer 8：dev_type、relationship_depth、core_indicator_02~06、industry
  - score 3：score_category、score_product、score_range
  - fabushi 6：fabushi_category、fabushi_remind_freq、fabushi_section_level、fabushi_badge_type、fabushi_znl_tag、fabushi_edition_type
- 索引 `idx_dict_type_module`

### 迁移后实测

- Flyway：`2026.11.16.5`
- 空库重放：**139 migrations / 0 failed / 8-8 断言码**
- 顶层顺序与流程协同子菜单正确
- 全库 `task:view` 菜单行 **1**，绑定 **3**（角色 0/06/admin）
- 角色 05：无 `task:view`，流程协同只看到提醒/审批
- 字典回填：customer 8 / score 3 / fabushi 6，共 17

## 3. PC 前端

- `layout/index.vue` 删除我的任务前端注入
- `router/index.js` task/reminder/approval 面包屑改「流程协同」
- `usePermissionData.js` 权限中心合并显示「流程协同」（权限码不变）
- `DictManager.vue`：左侧按模块分组、类型显示模块标签、新增/编辑模块下拉（含空模块）
- `api/system.js`：新增 `getDictModules`

实测：
- 侧栏顺序：首页 → 业务核心 → 流程协同 → 学分管理 → 法布施 → 统计分析 → 系统管理
- `/task` 标题「我的任务」
- 权限中心流程协同组含 task:view/manage、reminder:view/ensure、approval:view/approve/reject/transfer
- 字典分组：客户管理 8 / 学分管理 3 / 法布施 6

## 4. MP 前端

- `app.json`：tabBar 第 3 项改 `pages/fabushi/index` / 法布施；注册 `pages/task/index`（49 页）
- `custom-tab-bar`：TABS/PATHS/图标/选中态改法布施；新增书本 SVG 与 PNG
- `pages/fabushi/index.js`：tab 选中态、无权限回首页、onShow 刷新统计
- `pages/files/index.js`：移除 tab 选中态/无用 switchTab；作为普通页从应用中心 `navigateTo` 进入
- `pages/app/index.js`：应用中心顺序按拍板；`files` 改 navigateTo、`fabushi` 改 switchTab；流程协同增加 task
- 新增 `pages/task/*`：统计/筛选/列表/分页；完成（必填结果）、取消（必填原因）、转交（选人，复用 `/api/customer/users/simple`）
- `api/task.js`：新增 `completeTask`、`cancelTask`、`transferTask`
- 工作台「待办任务」卡可直达 `/pages/task/index`

实测：
- tabBar：首页/四项服务/法布施/应用/我的
- 应用中心顺序与流程协同 4 项正确
- 文件页从应用中心进入、可返回
- 我的任务页可加载；完成弹窗、转交弹窗（25 名可选人）可用；实际转交成功
- 临时任务已取消清理（id=1, status=cancelled）

## 5. 验证

| 项 | 结果 |
|---|---|
| 后端 | **1,103 tests / 0 failures / 0 errors / 0 skipped** |
| 前端 | **324 tests**；`npm run build` exit 0 |
| 小程序 | `node --check` 93/93；`dev-verify.sh miniprogram` PASS；49 页 |
| 一键门禁 | `bash scripts/gate.sh` **PASS=9 FAIL=0** |
| 空库重放 | PASS（139 / 0 failed） |
| 快照库 | Flyway `2026.11.16.5`，validate 通过 |
| PC smoke | 侧栏/任务页/权限中心/字典分组 PASS |
| MP smoke | tabBar/应用中心/文件页/任务页 PASS |
| 角色可见性 | admin/06 有任务；05 无任务；组按子菜单显示 |

证据：
- `test-results/gate/evidence-menu-dict-20261116.json`
- `test-results/menu-dict/evidence-20261116.json`
- `test-results/menu-dict/mp-smoke.json`、`mp-task-actions.json`
- `test-results/menu-dict/pc-menu-dict.png`、`mp-task*.png`

## 6. 测试环境部署

- 后端 jar md5 `0f748fbbfc8ae0e23ab3d300f7878232`，健康 UP
- 前端 index md5 `b86185e26cb0c0f476728151dfeb1a52`，HTTP 200
- 测试库 Flyway `2026.11.16.5`
- 备份：`/data/aigicrm/backup/app-pre-menu-dict-20261116-0242.jar`、`frontend-pre-menu-dict-20261116-0242.tgz`
- 生产未触碰

## 7. 回滚

- 代码：回退到 tag `test-fabushi-20261116` / 上一个 integration commit
- 后端：恢复 `/data/aigicrm/backup/app-pre-menu-dict-20261116-0242.jar`
- 前端：恢复 `/data/aigicrm/backup/frontend-pre-menu-dict-20261116-0242.tgz`
- 数据库：迁移前备份 `/tmp/pre-migrate-test-20261116-023530.sql`（86 表）；V15/V16 为增量，无删除业务数据
- 注意：16.4 已删除旧 `task:view` 菜单行；回滚需同时恢复该行/绑定或整库恢复
