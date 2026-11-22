# C10 三端一致性（PC / 小程序 / H5）— 静态对照（2026-11-18）

> 静态面；同数据同功能差分（运行时）留 W3。数据源：`module-inventory`（44 Controller/322 端点）、`mp-crosswalk`、`views` 目录统计。

## 一、页面覆盖对照

| 域 | PC `.vue` | MP 页 | H5 | 说明 |
|---|---|---|---|---|
| 2 客户 | 22 + dashboard 1 + indicator 1 | 12 + home/app 2 + indicator 1 | — | MP 覆盖主流程（列表/详情/表单/财务/联系人/分享/日志/筛选/指标） |
| 6 文件 | 10 | 4（含 picker 入口） | FilePicker | MP 走 web-view 上传（MP-TOKEN-001 影响面） |
| 7 激励 | 17 | 4 | — | PC 侧页面最多，MP 为轻量录入 |
| 5 审批 | 1 | 2 | — | PC 单页承载全部类型，MP 拆列表/详情 |
| 4 公海 | 2 | 2 | — | 对等 |
| 8 积分 | 2 | — | — | **MP 无积分页**（设计如此？待口径） |
| 9 统计 | 1 + ranking 1 | 2 + ranking 2 | — | 对等 |
| 10 提醒 | 2 | 1 | — | MP 单页 |
| 11 任务 | 1 | 1 | — | 对等 |
| 16 法布施 | 8 | 7 | FabushiReader | PC 管理端 8 页；MP 阅读端 7 页 + H5 阅读器 |
| 14 系统 | 27 | 9（profile） | — | MP 仅个人中心子集 |
| 17 公共 | error 2 + mp 2 | login 2 + app 1 | — | |

## 二、端点一致性（MP 侧）

- MP 实际调用 **40+ 条 API 路径**（`api/` + `pages/` 去重），覆盖认证/客户/文件/审批/提醒/公海/任务/激励/统计/法布施/草稿/公共 scope。
- 与后端 322 端点对照：MP 用的是子集；**无孤儿端点**（mp-crosswalk 93 码 vs 后端 95 码，仅 finance 为后端独有 legacy）。
- W2b 采样的 10 个只读端点均确认被 MP 调用（customer/file/approval/reminder/sea/task/score/statistics/fabushi/incentive 各一）。

## 三、已知差异（待 W3 运行时）

- **MP 无积分页**：PC 有 `score` 2 页；MP 51 页中无 score——是产品设计（MP 不做积分）还是遗漏，需口径确认（B4 相关）。
- **H5 两处**（FilePicker/FabushiReader）依赖微信 JSSDK + 主 JWT 进 URL——C7-NGINX-001 与 MP-TOKEN-001 的合并影响面。
- MP finance 权限码为后端独有 legacy（mp-crosswalk 已记）。

## 四、结论

- **C10 静态 = 覆盖对齐（页面/端点无孤儿）**，2 处待口径（MP 积分缺失、H5 两处）；运行时同数据差分 = BLOCKED（W3）。
