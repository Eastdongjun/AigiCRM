# 需求规格

## 目标用户与场景

- 角色：管理员/业务人员，在激励上传页按类别区域查看和上传激励文件
- 场景：打开激励上传页（/incentive/index.vue），顶部四区 Tab 顺序应为 产品试点→两谈两做方案→交流拜访→外出参观；上传弹窗"选择子项上传文件"分组顺序、系统设置类别管理分组顺序同步一致

## 事实

- 四区顺序由 `biz_incentive_category.sort_order` 驱动（IncentiveService.getCategoryTree 按 sort_order 升序，实测数据库当前为 visit=1/exchange=2/plan=3/trial=4）
- 前端所有消费方均取该接口顺序，无独立硬编码（index.vue:6/388/507、config.vue:155、customer.vue/overview.vue）
- 表格内记录排序已按 code 权重 trial→plan→exchange→visit，无需改动（index.vue:450-452）

## 假设

- 调整顶层 sort_order 即可全站统一，无需改代码（验证方式：改后 API + 浏览器实测）

## 阻断问题

无

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 激励四区（顶层类别）展示顺序为：产品试点(trial) → 两谈两做方案(plan) → 交流拜访(exchange) → 外出参观(visit)，未知类别排最后 | 用户输入 | Must |

## 业务规则与权限

- 无权限变化；排序仅影响展示顺序，不影响数据归属与统计
- 子项在其父区内保持原 sort_order（不调整）

## 异常与边界

- 若有未知/新增顶层类别（code 非四区），排最后（sort_order 最大即自然排最后，与现逻辑一致）
- 改序后默认 activeTab = parentCategories[0] = 产品试点（符合"试点最靠前"）

## 非功能需求

- 可逆：改前备份 sort_order，可一条 SQL 还原
- 兼容性：前端无改动，无缓存问题；后端无改动，无重构建

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given 测试服数据库，When 执行 4 条 UPDATE 后将顶层 sort_order 改为 trial=1/plan=2/exchange=3/visit=4，Then getCategoryTree 返回顺序为 产品试点→两谈两做方案→交流拜访→外出参观 |
| AC-002 | REQ-001 | Given 激励上传页，When 打开页面，Then 顶部四区 Tab 顺序为 产品试点→两谈两做方案→交流拜访→外出参观，默认选中"产品试点" |
| AC-003 | REQ-001 | Given 上传弹窗与系统设置类别管理，When 打开，Then 分组顺序同样为 产品试点→两谈两做方案→交流拜访→外出参观 |

## 追踪关系

- REQ-001 → AC-001、AC-002、AC-003
