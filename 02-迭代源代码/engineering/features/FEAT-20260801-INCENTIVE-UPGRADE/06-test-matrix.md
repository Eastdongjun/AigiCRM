# 测试矩阵

## 验收追踪
| AC | 测试 ID | 结果 |
|---|---|---|
| AC-101 | TEST-101 | PASS |
| AC-102 | TEST-102 | PASS |
| AC-103 | TEST-103 | PASS |
| AC-104 | TEST-104 | PASS |
| AC-105 | TEST-105 | PASS |
| AC-201 | TEST-201 | PASS |
| AC-202 | TEST-202 | PASS |
| AC-203 | TEST-203 | PASS |
| AC-204 | TEST-204 | PASS |
| AC-205 | TEST-205 | PASS |
| AC-206 | TEST-206 | PASS |
| AC-207 | TEST-207 | PASS |
| AC-208 | TEST-208 | PASS |
| AC-209 | TEST-209 | PASS |
| AC-210 | TEST-210 | PASS |
| AC-301 | TEST-301 | PASS |
| AC-302 | TEST-302 | PASS |
| AC-303 | TEST-303 | PASS |
| AC-304 | TEST-304 | PASS |
| AC-401 | TEST-401 | PASS |
| AC-402 | TEST-402 | PASS |
| AC-403 | TEST-403 | PASS |
| AC-404 | TEST-404 | PASS |
| AC-405 | TEST-405 | PASS |

## 静态与构建验证
- 前端 npm run build ✓ 342ms；后端 mvn clean package ✓（aigicrm-backend-1.0.0.jar）
- 服务器 dist 含新代码：curl 验证 incentive-Cb19IeYs.js 含"参观交流详情描述/现场照片" ✓

## 功能与集成验证
- TEST-101~102: 浏览器验证两级下拉（大类5选项、小类级联）✓ PASS(AC-101/102)
- TEST-103~105: 代码评审 watch 合成"大类-小类"提交值、追加回填拆分、列表列显示 ✓ PASS
- TEST-201~206: 清缓存浏览器直验（Playwright 全新上下文）：visit/exchange 弹窗"参观交流详情描述"+现场照片单槽、plan"文件详情描述"+安装文件单槽、trial 三槽+跟踪记录保留、单槽 5 个后无法添加、无文件提交按钮禁用、提交后 filePreId=[178] JSON 数组（mid/post 全 NULL）✓ PASS
- TEST-207~210: calcScore 新规则（plan 每文件+base 上限5×base；visit/exchange 任一槽+base；trial 三齐全）+ 重算接口 changed=3 实测 ✓ PASS
- TEST-301~302: customer-records API 实测四区排序（plan 前 exchange、trial 前 plan）✓ PASS
- TEST-303: index.vue mainOrderOf 前端排序实现（与后端同规则）✓ PASS
- TEST-304: search.vue latestRecord 最近更新解耦 ✓ PASS
- TEST-401~405: 搜索命中实测（造数据 id=68"涂层类-煤气管道"）：小类筛选→1客户精准过滤、关键字"煤气管道"→1客户、同事"王磊磊"→2客户、客户名"国能"→1客户、无匹配→0客户"暂无数据" ✓ PASS

## UI 验证
- 基线 1440x900 已保存；需求1 弹窗两级下拉截图 evidence/req1-two-level-select.png
- 视觉评分 88（record-ui-review 已记录）

## 回归验证
- npm test --prefix aigicrm-ui（既有用例）通过
- 需求1 页面交互回归：上传/追加弹窗打开正常、列表渲染正常

## 修复与风险（2026-08-01 15:xx 复验轮）
- **发现并修复 bug**：isSingleSlot/slotLabel/detailLabel 品类判定只在 code 为空时回退父级，而子分类 code 为 visit_factory/exchange_tech/plan_tech（非空），导致 visit/exchange/plan 弹窗误走旧 UI。修复：categoryCodeOf 上溯到顶层父级取 code（与后端 IncentiveService.java:220-226 父级回退逻辑一致）。已重新构建部署测试服务器并全量复验通过（curl 直连 404 系 nginx Referer 防盗链，浏览器直验为准）
- 顺带实测：需求1 两级下拉（AC-101/102/103）在清缓存浏览器直验通过（提交值"涂层类-煤气管道"落库正确）
- plan 旧记录分数可能上升（3→5×base 上限），已确认
- 测试库造数据：id=68 visit 记录（"涂层类-煤气管道"）用于搜索实测，保留
- 生产部署未授权
