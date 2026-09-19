# 设计文档：激励记录"具体产品"字段（产品试点专属，可选文本）

> 日期：2026-08-07
> 状态：已获用户批准
> 关联系统：AIGICRM/AigiCRM 激励模块
> 需求来源：客户新增需求

## 一、需求概述

在**产品试点（trial）**分类的激励上传详情中，在"相关产品"字段下方新增一个**"具体产品"文本框**：

- 可选、可编辑、非必填（可加可不加）
- 仅产品试点分类显示；两谈两做（plan）/ 交流拜访（exchange）/ 外出参观（visit）三个分类**不显示**
- 该字段同时显示在：激励查询详情页、客户卡片激励关联中
- 需要 UI 美化
- 参与激励查询页的关键字搜索（用户已确认）

## 二、产品契约

| 维度 | 定义 |
|---|---|
| 目标用户 | 销售员（录入产品试点案例时补充具体产品名） |
| 字段语义 | 相关产品=分类下拉（大类-小类）；具体产品=自由文本补充实际产品名/型号 |
| 核心任务 | trial 分类上传时可选填写具体产品；在查询/客户卡片中可见 |
| 成功标准 | ①trial 可填可不填并正确保存/编辑/清空 ②其他 3 分类不显示 ③查询详情与客户卡片展示 ④关键字搜索命中 ⑤积分不受影响 |
| 边界 | 仅 trial 分类；可选；不参与积分/统计；不进列表列 |
| 权限 | 沿用现有激励记录归属规则（本人 / can_edit 可编辑） |

## 三、数据模型

### 迁移脚本（1 个 SQL）

```sql
ALTER TABLE biz_incentive_record
  ADD COLUMN specific_product varchar(200) NULL AFTER related_products;
```

- 文件：`sql/migration/20260807/specific-product-column.sql`
- 新字段可选，**历史数据无需回填**（旧记录为 NULL，前端有值才显示）
- 生产执行规范：先备份 → 执行时带库名 → `--default-character-set=utf8mb4`

## 四、后端改动（3 处）

### 1. 实体 `BizIncentiveRecord`

新增字段（MyBatis-Plus 自动映射列名 `specific_product`，列表/查询接口自动带出）：

```java
private String specificProduct;   // 具体产品（可选，仅 trial 分类使用）
```

### 2. 编辑 `IncentiveService.updateRecord`（约 231-251 行）

在字段合并列表中加入：

```java
if (update.getSpecificProduct() != null) existing.setSpecificProduct(update.getSpecificProduct());
```

说明：`updateById(existing)` 只合并列出的字段；加此行后编辑可保存、清空（前端传空串 `""` 即清空，因 `"" != null`）。

### 3. 搜索 `IncentiveService.getCustomerRecords`（约 838-852 行）

关键字匹配从"客户名 OR 相关产品"扩展为"**客户名 OR 相关产品 OR 具体产品**"：

- 仅客户名分支：不变
- 关键字分支：`wrapper.like(relatedProducts, keyword)` → `.like(relatedProducts, keyword).or().like(specificProduct, keyword)`
- 客户名+关键字双分支：`w.in(customerId).or().like(relatedProducts, keyword).or().like(specificProduct, keyword)`

补充：`getCustomerRecords` 结果 map（约 807 行）加 `m.put("specificProduct", r.getSpecificProduct())`，供客户卡片使用。

### 明确不受影响的逻辑

- `calcScore`：只看文件槽位，**不涉及具体产品**，积分不变
- `addRecord`（POST /records）：实体插入自动带出新字段，无需改动
- `queryRecords`（/records/query）：product 筛选仍只匹配相关产品，本次不改（需求只要求主关键字搜索）

## 五、前端改动（3 个文件）

### 1. 激励上传 `aigicrm-ui/src/views/incentive/index.vue`

- 在"相关产品"字段块（约 169-177 行）下方，新增"具体产品"文本框
- **仅当当前分类顶层 code='trial' 时 `v-if` 显示**（复用现有 `categoryCodeOf` 上溯到顶层父级判定逻辑）
- 创建 payload（约 837/852 行）加 `specificProduct`
- 编辑回填（约 668 行）加 `specificProduct`

**UI 美化**：
- 与相关产品同一 `.dlg-field` 块，字号/间距一致
- 带"选填"提示标签（如 `具体产品 <span class="opt">选填</span>`）
- trial 专属浅色描边或辅助文案区分（如 placeholder："填写具体产品名称/型号，选填"）
- 保持与其他字段一致的 Element Plus `el-input` 风格

### 2. 激励查询 `aigicrm-ui/src/views/incentive/search.vue`

- 详情时间线（`dtl-info-line` 或新增 `dtl-field`）中显示具体产品
- `v-if="r.specificProduct"` 有值才显示，仿"安装前问题"展示样式

### 3. 客户卡片 `aigicrm-ui/src/views/customer/detail.vue`

- 激励条目（`.incentive-item`）中显示具体产品
- `v-if="r.specificProduct"` 有值才显示

## 六、连锁反应核查

| 连锁点 | 处理 |
|---|---|
| 激励上传/查询列表列 | 不加列（用户决策：仅详情显示） |
| 积分/统计 | 不受影响 |
| 数据范围/权限 | 沿用现有记录归属规则 |
| 导出 | 无激励记录导出端点，无需处理 |
| 关键字搜索 | 已接入 getCustomerRecords 主搜索 |
| 部署顺序 | 测试环境验证 → 生产：先迁移 SQL → 后端 jar → 前端 dist |

## 七、验证项（DoD）

1. trial 上传：具体产品可填可不填，保存后 DB 有值
2. 其他 3 分类（plan/exchange/visit）：不显示该字段
3. 编辑：回填 → 修改 → 保存 → 清空，全部生效
4. 激励查询详情：显示具体产品
5. 客户卡片激励关联：显示具体产品
6. 关键字搜索"具体产品名"能命中
7. 积分值与加字段前一致

## 八、部署说明

- 测试环境（TEST_SERVER_IP）：迁移 SQL → 构建后端 jar → 部署 → 前端 dist → 浏览器验证
- 生产环境（PROD_SERVER_IP）：**先迁移 SQL（带备份）** → 部署后端 jar → 部署前端 dist → 浏览器验证
- 注意：生产当前落后 15+ commit，本次部署将一并带上前序未上线功能；如只单独上线本功能，需确认生产基线后按增量处理
