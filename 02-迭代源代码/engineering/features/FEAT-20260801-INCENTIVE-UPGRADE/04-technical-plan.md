# 技术实施计划

## 已确认影响范围

- `aigicrm-ui/src/views/incentive/index.vue` — 需求2弹窗品类分支、需求3列表排序
- `aigicrm-ui/src/views/incentive/search.vue` — 需求3详情排序（含"最近更新"解耦）、需求4筛选
- `aigicrm-ui/src/views/customer/detail.vue` — 需求3激励 Tab 排序
- `aigicrm-backend .../incentive/service/IncentiveService.java` — calcScore 重写、customerRecords 加 product/keyword 参数、记录排序
- 测试部署：111.229.37.220（前端 dist + 后端 jar）

## 架构与关键决策

1. **品类判定复用 isPlanCategory 模式**：前端以 pendingChild 的 categoryCode 或父级 code ∈ {visit, exchange, plan} 判定单槽；后端新规则按分类 code 判定（plan=每文件计分；visit/exchange=任一槽有文件计分；trial=三齐全）
2. **后端统一排序**：customerRecords 分组内 records 按 品类序(trial→plan→exchange→visit)+createdAt desc 排序，detail.vue 与 search.vue 详情自动生效；index.vue 的 getRecords 列表在前端 enrichedRecords 排序（后端单表查询无法跨父子类排序，前端更简单）——**决策：统一后端排 customerRecords；index.vue 前端排**
3. **积分重写（calcScore）**：
   - plan：文件总数（pre+mid+post+track 不计）× base，上限 5×base
   - visit/exchange：pre/mid/post 任一有文件 → base
   - trial：三齐全 → base
   - 全量重算调既有 recalculateAllScores（POST /records/recalculate）
4. **搜索参数**：customerRecords 增加 `product`（relatedProducts like，小类名模糊即覆盖"大类-小类"）、`keyword`（relatedProducts like）——keyword 与 product 同字段，前端合并传参

## 数据、API 与事件契约

- `POST /api/incentive/records`：body 不变（relatedProducts="大类-小类" 字符串；filePreId=JSON数组）
- `GET /api/incentive/customer-records`：新增 query 参数 `product`（小类名，like relatedProducts）、`keyword`（like relatedProducts）
- `GET /api/incentive/records`：返回顺序改为前端排序（不依赖后端）
- 重算：`POST /api/incentive/records/recalculate`（既有接口，无需改动）

## 权限与安全边界

- 无新增权限点；customer-records 的 computeVisibility 数据权限不变
- 重算接口既有管理员校验不变

## 实施顺序

1. IncentiveService.java：calcScore 重写 + customerRecords 排序与参数 → mvn 编译
2. index.vue：弹窗品类分支（singleSlotMode 计算、字段名、跟踪记录隐藏、单槽提交校验、重置/追加同步）+ 列表排序
3. search.vue：详情时间线排序 + "最近更新"解耦 + 高级筛选新增小类/同事姓名 + 主搜索 keyword
4. detail.vue：激励 Tab 排序（records 已按后端序返回）
5. 构建 → 部署前端+后端 → 重算 → 浏览器验证

## 测试策略

- 后端单测：IncentiveScoreTest 扩展新规则用例（plan 1文件/5文件/0文件、visit 1文件、trial 三齐全/不齐）
- E2E：浏览器四区上传弹窗分支（visit/exchange/plan/trial）、单槽校验、排序、查询
- 回归：npm test 全量

## 迁移、发布与兼容

- 无数据迁移；历史记录展示兼容（getFiles 读三槽）
- 重算后分数变化是预期行为（用户已确认）
- 前端先部署，后端随后（或同批），接口向后兼容（新参数可选）

## 可观测性

- 重算接口返回 changed 数；验证时回读数据库抽查
- 无新增日志

## 回滚

- 前端：git 无版本管理（工作区直接改），保留 index.vue.bak 模式——先备份改动文件，回滚=恢复备份+重新构建部署
- 后端：重算不可逆——回滚需按旧规则再重算一次（旧 calcScore 逻辑留档）

## 风险与盲区

- index.vue 列表排序依赖 enrichedRecords 中 categoryMap 的父级 code（getRecords 填充的 categoryId 需在分类树中存在，测试库有 46 号脏数据分类——排序兜底：未知 code 排最后）
- plan 历史记录分数上升（最多 3→5×base），用户已知
- 单槽追加时 filePre 槽内已有文件计数正确性（_existing 排除新文件）
