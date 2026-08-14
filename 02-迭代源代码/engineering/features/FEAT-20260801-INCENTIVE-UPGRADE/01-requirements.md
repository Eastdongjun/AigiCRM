# 需求规格

## 目标用户与场景

- 销售/技术同事：在激励上传页提交激励记录（上传文件、关联客户），在激励查询首页检索案例
- 管理层：在客户详情页/查询页查看客户四项激励全貌
- 触发场景：上传激励文件（含追加）、编辑回填、查询检索、查看客户激励

## 事实

- 分类编码（SQL biz_incentive_category）：visit=外出参观、exchange=交流拜访、plan=两谈两做方案、trial=产品试点
- 需求1 代码已写入 index.vue（productCats 常量、relatedCat/relatedSub 两级 ref、watch 合成 relatedProducts、提交/回填/清空同步），未经构建验证
- 后端 `getCustomerRecords`（/incentive/customer-records）已支持 customerName/salesperson/industry/mainCategoryId/orgIds/devType/startDate/endDate 参数，salesperson 为 realName like 匹配；**不支持 relatedProducts 搜索**
- 后端 `calcScore`：plan 三槽各自独立 +base；其他三齐全 +base
- 详情页 detail.vue 与 search.vue 详情均消费 `/incentive/customer-records` 返回值（records 数组内 createdAt 倒序）
- 用户确认（2026-08-01）：plan 单槽按文件数计分（每文件+base，上限5×base）；visit/exchange 单槽 ≥1 文件即 +base；trial 保持现状

## 假设

- visit/exchange 的"参观交流详情描述" = 现有 `preInstallQuestion` 字段改名（数据不迁移），"安装备注"保留
- plan 的"文件详情描述" = 同样映射 `preInstallQuestion` 改名
- 单槽新文件存 `filePreId`（JSON 数组，1~5）；trial 继续三槽
- 历史三槽数据在前端展示层（getFiles 等）保持兼容读取
- 需求1 选项表以交接文件为准（防滑板类[公共区/装置区]；分类树中"楼梯板类"与其并存不冲突）
- 需求4 "同事姓名搜索"复用后端 salesperson 参数（already like 匹配）；"关键字"匹配 `relatedProducts`（like）
- 需求4 产品小类下拉选项 = 需求1 相同选项表（大类-小类），选中后按小类名 like 匹配 relatedProducts

## 阻断问题

无（积分规则与 trial 范围已由用户确认）

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-101 | 相关产品两级下拉：大类+小类，提交"大类-小类"，编辑回填拆分，历史无横杠文本兼容 | 交接文件 | Must |
| REQ-201 | 弹窗按品类改名：visit/exchange 安装详情→"参观交流详情描述"、安装文件→"现场照片"；plan 安装详情→"文件详情描述" | 交接文件 | Must |
| REQ-202 | visit/exchange/plan 安装文件单槽化：1~5 个（至少1最多5，不足1不可提交）；数据存 filePreId | 交接文件+用户确认 | Must |
| REQ-203 | visit/exchange/plan 弹窗隐藏"跟踪记录"上传区；trial 保留 | 交接文件+用户确认 | Must |
| REQ-204 | 积分规则重写：plan 每文件+base（上限5×base）；visit/exchange ≥1文件+base；trial 三齐全+base；全量重算历史 | 用户确认 | Must |
| REQ-301 | 客户相关激励记录按 trial→plan→exchange→visit 排序（同品类内 createdAt 倒序），覆盖 index.vue 列表、search.vue 详情时间线、detail.vue 激励 Tab | 交接文件 | Must |
| REQ-401 | 查询首页增加产品小类筛选（选项同需求1），后端 relatedProducts like 匹配 | 交接文件 | Must |
| REQ-402 | 查询首页支持同事姓名（模糊）搜索，其共享案例全出 | 交接文件 | Must |
| REQ-403 | 查询首页主搜索框同时支持客户名与关键字（如"煤气管道"），关键字匹配 relatedProducts | 交接文件 | Must |

## 业务规则与权限

- 数据权限：/incentive/customer-records 已按 computeVisibility 过滤（admin/有编辑删除权限看全部；否则数据范围+客户分享），需求4新增参数不改变此过滤
- 上传/追加权限：沿用现有 canEdit/canDelete 检查
- 积分计算唯一入口 calcScore（上传、追加、重算共用）
- 追加模式（startReplace）：单槽品类追加时仅保留 filePre 槽 UI；trial 追加保持三槽

## 异常与边界

- 历史数据无 relatedProducts（null/空）→ 下拉回填为空，选择大类后重新提交
- relatedProducts 含横杠多段（如"涂层类-防腐"）→ 拆分为 大类=涂层类、小类=防腐；仅一段 → 大类空、小类=原值
- 单槽 1~5 校验：0 个文件时"上传并提交"按钮禁用并提示；第 6 个文件无法添加
- visit/exchange/plan 旧三槽数据展示：读 pre/mid/post 全保留显示（搜索页 getFiles 兼容）
- 重算后分数变化不单独通知（排行榜自然反映）
- 搜索关键字为空 → 全量；关键字匹配不到 → 空结果"暂无数据"
- 需求3排序后 search.vue 详情"最近更新"取最新 createdAt 的记录，不随排序改变

## 非功能需求

- 前端构建 npm run build 通过；后端 mvn clean package -DskipTests 通过（JAVA_HOME=openjdk@21）
- 排序与搜索在现有数据量（数百条）下无需索引优化，保持 in-memory 或现有 SQL 模式
- 改动保持现有 UI 风格与设计 Token（沿用 index.vue/search.vue 现有样式体系）

## 验收标准

| 验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-101 | REQ-101 | Given 打开上传弹窗 / When 点击相关产品 / Then 显示大类下拉（涂层类/防滑板类/机封类/密封类/润滑类）与级联小类下拉 |
| AC-102 | REQ-101 | Given 未选大类 / When 操作小类下拉 / Then 小类禁用；选大类后小类选项随大类变化 |
| AC-103 | REQ-101 | Given 已选"涂层类-防腐" / When 提交 / Then 保存值为"涂层类-防腐" |
| AC-104 | REQ-101 | Given 编辑含"涂层类-防腐"的记录 / When 打开追加弹窗 / Then 大类=涂层类、小类=防腐；含"防腐"（无横杠）时小类=防腐、大类空 |
| AC-105 | REQ-101 | Given 列表存在相关产品记录 / When 查看列表 / Then 相关产品列显示"大类-小类" |
| AC-201 | REQ-201 | Given 品类为外出参观/交流拜访 / When 打开上传弹窗 / Then 安装详情显示"参观交流详情描述"、安装文件显示"现场照片" |
| AC-202 | REQ-201 | Given 品类为两谈两做方案 / When 打开上传弹窗 / Then 安装详情显示"文件详情描述"、安装文件为单槽 1~5 |
| AC-203 | REQ-202 | Given visit/exchange/plan 上传弹窗 / When 未选任何文件点提交 / Then 禁止提交（提示至少1个文件） |
| AC-204 | REQ-202 | Given 单槽已添加5个文件 / When 再点添加 / Then 无法添加第6个（计数 5/5） |
| AC-205 | REQ-202 | Given 单槽提交成功 / When 查数据库 / Then filePreId 为 JSON 数组且长度为文件数 |
| AC-206 | REQ-203 | Given visit/exchange/plan 上传弹窗 / When 查看弹窗 / Then 无"跟踪记录"区；trial 弹窗仍有跟踪记录区 |
| AC-207 | REQ-204 | Given plan 记录含1个文件 / When 提交 / Then 得分=base；含5个文件得分=5×base |
| AC-208 | REQ-204 | Given visit/exchange 记录含≥1个文件 / When 提交 / Then 得分=base |
| AC-209 | REQ-204 | Given trial 记录仅pre槽有文件 / When 提交 / Then 得分=0（三齐全规则不变） |
| AC-210 | REQ-204 | Given 重算接口执行 / When 完成 / Then 全部记录按新规则得分，无异常 |
| AC-301 | REQ-301 | Given 客户有4类激励记录 / When 打开 detail.vue 激励 Tab / Then 顺序为 试点→两谈两做→交流拜访→外出参观 |
| AC-302 | REQ-301 | Given search.vue 客户详情 / When 打开 / Then 时间线按 trial→plan→exchange→visit 组内排序，同品类按时间倒序 |
| AC-303 | REQ-301 | Given index.vue 列表 / When 切换任一 Tab / Then 记录按品类排序（同品类 createdAt 倒序） |
| AC-304 | REQ-301 | Given search.vue 详情含多记录 / When 查看"最近更新" / Then 显示最新 createdAt 记录（不随排序变为首条） |
| AC-401 | REQ-401 | Given 查询首页 / When 选择产品小类（如"煤气管道"）并查询 / Then 结果仅含 relatedProducts 含"煤气管道"的记录 |
| AC-402 | REQ-402 | Given 查询首页 / When 输入同事姓名（模糊）查询 / Then 该同事共享案例全部出现（含分享客户） |
| AC-403 | REQ-403 | Given 查询首页 / When 输入客户名查询 / Then 该客户共享内容全出 |
| AC-404 | REQ-403 | Given 查询首页 / When 输入关键字"煤气管道"查询 / Then relatedProducts 含"煤气管道"的案例全出 |
| AC-405 | REQ-403 | Given 查询首页 / When 输入无匹配文本查询 / Then 显示"暂无数据" |

## 追踪关系

REQ-101 → AC-101~105 → TEST-101~105（需求1 构建验证 + 弹窗交互验证）
REQ-201/202/203 → AC-201~206 → TEST-201~206（需求2 弹窗品类分支验证）
REQ-204 → AC-207~210 → TEST-207~210（积分规则 + 重算验证）
REQ-301 → AC-301~304 → TEST-301~304（三处排序验证）
REQ-401/402/403 → AC-401~405 → TEST-401~405（搜索验证）
