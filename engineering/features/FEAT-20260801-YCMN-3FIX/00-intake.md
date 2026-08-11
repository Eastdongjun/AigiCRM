# 功能受理

## 用户目标

修复 YCMN 测试服 3 个问题：①激励安装文件点击不弹上传 ②公海池可见范围校验 ③新增用户必填。

## 当前行为

### 问题①：激励上传弹窗内点"+"不弹文件选择框
- 复现：激励上传页 → 点"上传文件"/表格行 → 弹窗打开 → 文件槽位卡片初始被折叠（`.dlg-body` 内容高 924px，视口 max-height 62vh=558px，scrollTop=0），"+"卡片（y=624~724）下缘被底部按钮区（y=633 起 `.dlg-footer-btns`）覆盖，elementFromPoint 命中 footer 而非卡片 → 点击无反应。
- 即使滚动后点击卡片，CDP 实测 `navigator.userActivation.isActive=false` 时 `input.click()` 被 Chrome 静默忽略（file chooser 需要 user activation）。布局重叠使可点击区域极小，用户点击大概率落在 footer 上。
- 部署版（incentive-BQqAaFz-.js 23KB）与源码 dist 完全一致（2026-08-01 15:15 构建，测试服已部署最新）。

### 问题②：公海池捞取/操作接口缺可见范围校验
- 现状：列表/统计已按可见范围过滤（`SeaService.visiblePoolIdsOrNull` + `scoped`，实测销售员 wangleilei 列表 0 行正确）。
- 缺口：`ApprovalService.submit(type=claim)` 只校验"客户在公海中"和"未被他人申请"，**不校验申请人是否可见该客户所在公海池** → 非管理员可直接 POST 捞取不可见池客户。

### 问题③：新增用户必填校验失效
- 前端 `user.vue` formRules 定义 realName/username/roleId/phone 必填，但"账号/姓名/角色"三个 `el-form-item` 未绑定 `prop` → Element Plus rules 不触发，只有手机号生效。
- 后端 `SysUserService.create()` 只校验用户名唯一性，无必填兜底 → 空账号/空姓名/无角色可入库。

## 范围

1. 修复激励上传弹窗布局：文件槽位首屏可见、"+"/文件卡片不被底部按钮区覆盖、点击可正常弹出文件选择框。
2. 公海池捞取申请接口增加可见范围校验（申请人须可见目标客户所在公海池，否则拒绝）。
3. 新增用户：前端补全必填 prop（账号/姓名/角色），后端 create 增加必填兜底校验。
4. 构建前端并部署测试服，验证 3 项修复。

## 不在范围

- 不改激励上传的业务逻辑（单槽/三槽、积分规则、追加保留逻辑）。
- 不改公海池配置页（可见部门/人员选择、办事处重叠校验已存在）。
- 不加新必填字段（如所属组织是否必填不在本次范围）。
- 不动生产环境；不重算积分。
- 浏览器缓存旧 chunk 导致的用户端问题（建议用户强刷）。

## 来源与事实

- 用户确认：①=弹窗内点"+"不弹文件选择框；②=捞取/操作接口加可见校验。
- 测试服：111.229.37.220（ubuntu），前端容器 ycmn-frontend（nginx，/usr/share/nginx/html），后端 ycmn-backend，库 shared-mysql（ycmn），Redis shared-redis。
- 源码基准：`/Users/donglai/东来/01-Projects/01-aigicrm/04-源码`（main，与部署一致）。
- 部署方式（记忆）：dist tar → scp → docker cp ycmn-frontend；jar → docker cp ycmn-backend。
- 测试账号：admin/admin口令(经env注入)（系统管理员）；wangleilei/admin口令(经env注入)（销售员，本次已重置密码并清 Redis 登录失败锁）。
- 参考资料：docs/客户添加与公海池配置注意事项.md；sql/migration-sea-visible.sql。

## 约束

- 后端 BCrypt 密码（勿用 MD5 直接改密码）；测试库密码可重置。
- 测试服 nginx 有 Referer 防盗链（浏览器 200 / 无 Referer 404）。
- 前后端改动必须最小化，保留现有业务行为。
- 需重新构建前端（vite build）与后端 jar（mvn package）。

## 初始风险

- 弹窗布局修复若直接改 62vh 数值，不同分辨率下可能仍截断 → 采用弹窗定高 + body 独立滚动方案。
- 后端补校验可能影响现有捞取流程（如管理员代理捞取）→ 校验仅针对非 all 数据范围用户；admin 不受影响。
- 前端 dist 需在测试服容器内替换，注意备份原文件与 Referer 防盗链下载限制。
