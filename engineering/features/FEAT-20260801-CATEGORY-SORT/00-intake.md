# 功能受理

## 用户目标

激励上传页（/incentive/index.vue）的四区顺序改为：**产品试点（最靠前）→ 两谈两做方案 → 交流拜访 → 外出参观（最靠后）**。用户原话："激励上传的排序 顺序 改一下 建议是分成四个区域：试点（最靠前）；两谈两做方案；交流拜访；外出参观（最靠后）"。

## 当前行为

（2026-08-01 19:45 实测，测试服 111.229.37.220）

- 四区顺序由后端 `IncentiveService.getCategoryTree()` 按 `biz_incentive_category.sort_order` 升序返回决定（ycmn-backend `IncentiveService.java:81-85`）
- 数据库当前 sort_order：外出参观 visit=1、交流拜访 exchange=2、两谈两做方案 plan=3、产品试点 trial=4 → 页面四区顺序为 外出参观→交流拜访→两谈两做方案→产品试点（与目标正好相反）
- 消费方（全部随 tree 顺序联动，无独立硬编码）：
  1. `ycmn-ui/src/views/incentive/index.vue:6` 顶部四区 Tab（cat-tab）+ `:388` parentCategories + `:507` 默认 activeTab = parentCategories[0]
  2. 上传弹窗"选择子项上传文件"分组（index.vue 44-63 行）
  3. `ycmn-ui/src/views/system/config.vue:155` 类别管理一级卡片分组（`top.categoryName` 遍历 tree）
  4. `incentive/customer.vue`、`overview.vue` 同用 getCategoryTree
- 注意：index.vue `filteredRecords` 表格内排序已是 code 权重（trial→plan→exchange→visit，`index.vue:450-452`），无需改动——只有区域/Tab 顺序与目标相反

## 范围

- 数据库（测试服 ycmn.biz_incentive_category）：4 条 UPDATE，将顶层 sort_order 改为 trial=1、plan=2、exchange=3、visit=4；改前导出备份
- 前端/后端代码：零改动（排序本就由 sort_order 驱动）
- 验证：getCategoryTree API 顺序 + 浏览器实测上传页四区 Tab、上传弹窗分组、config.vue 分组

## 不在范围

- 生产环境部署（需用户另行授权）
- 子类 sort_order（同父下子项顺序不变）
- 四区显示形态/样式调整（本次仅顺序）

## 来源与事实

- 用户输入："激励上传的排序 顺序 改一下 建议是分成四个区域：试点（最靠前）；两谈两做方案；交流拜访；外出参观（最靠后）"
- 数据库实测：SELECT id, category_name, category_code, parent_id, sort_order FROM ycmn.biz_incentive_category WHERE parent_id=0 → 1 外出参观 visit/1、2 交流拜访 exchange/2、3 两谈两做方案 plan/3、4 产品试点 trial/4

## 约束

- 测试服 111.229.37.220（ubuntu/redacted-ssh-password，sshpass），MySQL 容器 shared-mysql，库 ycmn
- 生产未部署；测试服数据修改可逆（备份 + UPDATE 可回滚）

## 初始风险

- 低：数据变更仅 4 行 sort_order，消费方全部走同一接口，无代码耦合风险
