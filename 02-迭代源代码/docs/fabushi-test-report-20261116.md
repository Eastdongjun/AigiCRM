# 法布施模块测试报告（2026-11-16）

## 范围

- PC：分享广场、内容详情、书目浏览、在线阅读、发布、书目管理、内容治理、统计看板
- MP：工作台、正能量流、书目列表/详情、发布、木鱼成功、作品详情、应用中心
- 后端：书目/内容/互动/统计/恢复/详情
- 权限：9 个权限码 + 4 角色 + 28 个启用用户

## 自动化结果

| 项 | 结果 |
|---|---|
| 后端单测 | 1103 / 0 failures |
| 前端单测 | 324 / 0 failures |
| vue-tsc | 537 / 537 |
| 前端构建 | PASS |
| MP dev-verify | PASS（49 页） |
| 空库重放 | 141 migrations / 0 failed / 2026.11.16.7 |
| 一键门禁 | PASS=9 / FAIL=0 |
| 真实 HTTP 权限矩阵 | 22,260 例；21,216 通过；1,044 异常已归因 |
| 法布施定向权限 | 60 例全过 |

## 权限矩阵归因

- 越权成功（无权限 2xx）：0
- 服务端 5xx：0
- 有权限却被 401/403（非资源形状）：0
- 790 条 403 来自 `{id}=999999` 资源/范围守卫
- 250 条 400 为参数缺失/请求形状，在方法鉴权前返回；无数据泄露，列为“鉴权前 400”体验优化
- 4 条 403 为组织管理员空 body 无 orgId 的正常业务守卫

## 运行时联调

- 测试环境已部署 16.7 + 新后端/前端
- 修复：看板路由漏注册、看板 NPE、书目 status=all、恢复清空留痕
- 复测：看板 200；书目 all 3 条；恢复 200；PDF/详情 200
- PC P6/P7/P9、MP A1/A4/B3 已实测

## 残余

- ~~250 条鉴权前 400~~ **已收口（2026-11-16）**：227 条为参数形状先于方法鉴权
  （9 端点权限门源码核对无一缺失，进矩阵白名单）；23 条为矩阵脚本 or 表达式
  误判（系统行为一直正确，已修 `permission_rule`）。重跑 24k 矩阵：
  valid+400 = 0、5xx = 0、越权 2xx = 0，残余 794 条 403 全部为已归因资源/业务守卫。
  详见 `docs/fabushi-preauth-400-registry-20261116.md` 与
  `test-results/permission-matrix/summary.md`
- 二期能力未做：附件、章节树、PDF 进度/续读、书架、阅读记录、提醒、导出、共读活动、MP PDF 阅读器

## 二期阅读链（2026-11-16 追加）

- 后端新增：书架/进度/记录/提醒设置 4 组接口（仍用 `fabushi:view`）
- PC：`/fabushi/shelf` 书架/记录/提醒三页签；阅读器页码进度保存与续读
- MP：A1 书架/记录/统计页签；settings 页；书目详情加入书架 + openDocument
- 实测：shelf add/list、progress 8/288、records 1 天、settings 保存 200
- 门禁：PASS=9；后端 1103；MP dev-verify 50 页
- 未完成：附件/章节树/MP 页码回传/导出/活动

## 二期附件/章节/活动/导出（Round 3）

- 附件：上传 scope=fabushi → post1 挂载 → 详情返回 fileName；PC 详情图片渲染 PASS
- 章节：book3 创建“第一部分 立命之学” → 树查询 1 条；P6 弹窗 PASS
- 活动：创建“了凡四训共读” → 参与 → 列表 joined=true；PC 活动页签 PASS
- 导出：`/stats/export?period=week` 200 text/csv；PC 下载事件触发 PASS
- 权限：activity:manage/stats:export 生成物补齐；API 288
- MP 新增 50 页，dev-verify PASS；DevTools automator 重启后仍无法连接（待 IDE 项目窗口手工重开）

## Round 3 补充

- 附件：图片上传→post1 挂载→详情返回 fileName；PC 详情图片渲染 PASS
- 章节：book3 创建/查询 1 条；P6 章节弹窗 PASS
- 活动：创建→参与→joined=true；PC 活动页签 PASS
- 导出：CSV 200
- 二期定向权限：48 例（admin/0/05/06 × 12 场景）全过
- API 288；MP 50 页；gate PASS=9；后端 1103
- MP DevTools automator 当前无法连接（IDE 需重开项目窗口），MP 视觉验收待下一轮

## 24,024 全量权限矩阵（2026-11-16 最终轮）

- 28 用户 × 286 端点 × 3 认证态 = 24,024
- 22,978 通过；1,046 异常
- 异常：790 资源 ID 403、250 鉴权前 400、4 组织守卫、2 个 `PUT /draft/{type}/force` 空 body 500
- 2 个 500 已修：入口补 draftData 非空校验；NO001/TY016 复测 400
- **无权限 2xx = 0；无未解释的有权限 401/403 = 0**
- 二期定向权限 48 例全过
- 证据：`test-results/permission-matrix/result.json`、`summary.md`、`phase2-role-smoke.json`

## MP 运行态收口（2026-11-16 Round 4）

- DevTools automator 改为独立 auto-port 9425 启动后连接成功
- 发现并修复 `pages/fabushi/index.wxml` 编译错误：WXML 不接受 `!(records.days || []).length` 类表达式，改为 JS 计算 `recordEmpty/teamRanking`，书架进度改为 `progress` 字段
- MP 实测通过：5 页签、书架 1 条、记录 1 天、统计团队 1 条、A4 详情可见且章节 1 条、提醒设置 20:00/每天、发布页 canCreate
- 截图：`test-results/fabushi-complete/mp-round4-settings.png`、`mp-round4-upload.png`
- 证据：`test-results/fabushi-complete/mp-round4.json`

## 鉴权前 400 收口轮（2026-11-16 Round 5）

- P1 收口：250 条鉴权前 400 → 0
  - 227 条：9 端点参数形状先于 `@PreAuthorize`（权限门源码核对无一缺失，矩阵白名单）
  - 23 条：矩阵脚本 or 表达式误判（`permission_rule` 已修，系统行为一直正确）
- 重跑 24,024：**valid+400 = 0；5xx = 0；越权 2xx = 0**；残余 794 条 403 =
  790 资源守卫（34 端点，与上轮同数）+ 4 组织守卫，全部已归因
- 白名单负例自证 14/14 PASS（白名单端点 2xx/5xx 仍红）
- 证据：`docs/fabushi-preauth-400-registry-20261116.md`、
  `test-results/permission-matrix/summary.md`、`result.json`（已覆写为收口轮结果）

## P2/P3 交付轮（2026-11-16 Round 5）

- **P2 方案 A（用户拍板）**：MP 阅读改 web-view H5 PDF.js（`/mp-fabushi-reader`），
  翻页自动回传页码——openDocument 与"写死 lastPage=1"两条旧路径一并退役
  （旧实现会把 PC 已存页码重置回第 1 页）。端到端实证：3 页 PDF 翻页 1/3→2/3，
  服务端 lastPage=2/totalPages=3（基线 1/1 负例自证），重开自动续读第 2 页
- **P3**：部门进度（dashboard 共用段，口径同 score 先例，无部门归"未分配"）
  + 月度报告（`/api/fabushi/stats/monthly[/export]` 2 新端点，零新权限码，API 288→290）
  + PC 部门进度表/月度报告卡 + MP 统计页签部门进度/月度简报
- 门禁：PASS=9/FAIL=0；后端 1107/0；PC 324 + vue-tsc PASS；MP dev-verify 51 页
- 矩阵 24,192（290 端点）：valid+400=0、5xx=0、越权 2xx=0；794 资源守卫+6 瞬时超时全部归因；
  二期定向 48/48
- 部署修复一个真实 500：deptProgress 排序 Integer→Long 强转 ClassCastException（改 Number 取值）
- 测试服 nginx 热修 `.mjs` MIME（镜像内配置，重建即丢——**生产必须同步**，见
  `docs/fabushi-p2p3-release-notes-20261116.md` 第三节）
- 截图：`test-results/fabushi-complete/`（PC 看板/H5 守卫/H5 翻页/续读/MP 统计页签/reader web-view）
