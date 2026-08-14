# 功能受理

## 用户目标

继续四项激励改造（继承自 FEAT-20260801-FIX-PARSEID 交接 `NEXT-INCENTIVE-UPLOAD.md`，2026-08-01）：
1. 相关产品自填 input → 两级下拉（**代码已改完，未构建部署**）
2. 安装详情按品类改造（visit/exchange/plan 单槽化、字段改名、删除跟踪记录上传）
3. 客户激励记录按四区排序（试点→两谈两做→交流拜访→外出参观）
4. 激励查询首页搜索完善（产品小类/同事姓名/客户+关键字）

## 当前行为

- 上传弹窗（`index.vue`）：相关产品为自填 input；安装详情 = "安装前问题"+"安装备注"；安装文件 = 安装前/中/后 三槽（各≤5）；跟踪记录区可传文件（不加分）
- 积分规则（后端 `IncentiveService.calcScore`）：
  - plan（两谈两做）：pre/mid/post 每槽有文件各 +base（最多 3×base）
  - 其他（visit/exchange/trial）：三槽齐全才 +base
- 排序：所有记录列表按 createdAt 倒序（index.vue 表格、search.vue 详情时间线、detail.vue 激励 Tab）
- 查询首页（`search.vue`）：客户名/销售人员/行业/开发类型/日期范围/组织 筛选，调 `/incentive/customer-records`
- 分类编码（biz_incentive_category，SQL 证据）：外出参观=visit、交流拜访=exchange、两谈两做方案=plan、产品试点=trial；sort_order 为 visit(1)/exchange(2)/plan(3)/trial(4)

## 范围

- `ycmn-ui/src/views/incentive/index.vue` — 需求1（已改）、需求2弹窗改造、需求3列表排序
- `ycmn-ui/src/views/incentive/search.vue` — 需求4筛选栏 + 需求3详情时间线组内排序
- `ycmn-ui/src/views/customer/detail.vue` — 需求3激励 Tab 排序
- `ycmn-backend IncentiveService.java` — calcScore 重写、customerRecords 搜索参数、排序
- 部署测试服务器 111.229.37.220（前端 npm run build → tar → scp → docker cp ycmn-frontend；后端 mvn package → jar）

## 不在范围

- trial（产品试点）：保持三槽+跟踪记录，积分规则不变
- 生产环境部署（未授权，仅测试服务器验证）
- 其他页面/模块

## 来源与事实

- 交接文件 `NEXT-INCENTIVE-UPLOAD.md`（需求2/3/4 原文，需求1选项表）
- 现有代码：index.vue / search.vue / detail.vue / IncentiveService.java / IncentiveController.java / 分类表 SQL
- 用户 2026-08-01 口头确认：plan 按文件数计分（每文件+base，上限5×base）；trial 不动

## 约束

- 数据模型不迁移：单槽新文件存 `filePreId`（JSON 数组）；历史三槽数据展示兼容读取
- 前端 vue3 + element-plus；后端 Spring Boot + MyBatis-Plus
- 测试环境部署走既有脚本流程（docker 容器 ycmn-frontend）

## 初始风险

- **积分重算口径**：calcScore 统一按新规则（plan=每文件+base 上限5×base；visit/exchange=任一槽有文件即+base；trial=三齐全+base），再调 recalculateAllScores 全量重算。后果：plan 旧记录分数可能上升（原最多3×base）；visit/exchange 旧不齐记录由 0 分变 +base（用户已确认新规则，接受分数变化）
- plan 积分上限 3×base → 5×base，排行榜分数变化（用户已知晓）
- 需求1 代码未经构建验证，可能有语法/交互问题
- 需求3排序影响 search.vue 详情"最近更新"取 records[0] 的逻辑——需同步调整为按时间取首条，避免"最近更新"被排序打乱
