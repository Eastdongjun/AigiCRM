# PC ↔ 小程序权限与 API 对照矩阵(审计阶段 7/8 产出,2026-09-22)

> 方法: grep 实采两端调用(PC src/api/*.js 74 端点 / 小程序 api/*.js 64 端点字面+模板),
> 与 01-api-permission-inventory.md(213 端点)对齐; 生成于 commit 68d62733。
> 已知形态差异: 小程序大量使用 `'/api/x/' + id` 模板拼接, 字面 diff 会误报 only-mini,
> 已人工归一(file/upload、draft、incentive 子路径等均为两端同端点不同调用风格)。

## 一、模块级 Parity

| 业务域 | PC | 小程序 | 后端权限码 | 一致性 |
|---|---|---|---|---|
| 客户 查看/增/改/删/分享/释放 | ✅ 列表+详情+高级筛选 | ✅ 列表+筛选+详情+编辑入口 | customer:view/add/edit/delete + share(经edit) | ✅ 对齐(2026-09-21 批次修复保护状态域后) |
| 客户 导入/导出 | ✅ | ❌ 无 | customer:import/export | PC-only(设计如此,移动端不做批量) |
| 联系人/财务 | ✅ Tab+按钮 | ⚠️ 详情只读字段 | contact:manage/finance:view/manage | 小程序无写入口(API 可达但 UI 未暴露, 后端双端同守卫) |
| 四项服务 查看/上传/改/删 | ✅ | ✅(含两级分类+拨盘详情) | incentive:view/add/edit | ✅ 对齐(主要佐证槽 2026-09-21 对齐) |
| 激励统计/阅读排行 | ✅ | ✅(统计+钻取) | incentive:view/read-stats/read-ranking:view | ✅ |
| 文件 四scope/上传/下载/分享/ACL | ✅ | ✅ 四scope+上传下载分享; ACL 仅 PC | file:*(acl:manage PC-only) | ✅ 对齐(2026-09-21 修复激励tab筛选后); ACL 属管理面 PC-only 合理 |
| 公海 查看/捞取/删除 | ✅ | ✅ 查看+捞取 | sea:view/claim(删除池级ACL) | ✅ |
| 公海配置/导入 | ✅ | ❌ | sea:config:*/import | PC-only(管理面) |
| 审批 | ✅ 列表+操作 | ✅ 列表+详情+通过/驳回(app/api: list/{id}/approve/reject/statistics/submit) | approval:* | ✅ 对齐(2026-09-22 纠错: 初版误记"无页面", 实有 pages/approval/index+detail, 见 app.json 46页清单) |
| 提醒 | ✅ | ⚠️ 首页卡片(只读) | reminder:view | 只读子集,合理 |
| 任务 | ✅ | ❌ 无页面 | task:view/manage | PC-only(阶段7/8记录: 移动端未规划) |
| 积分 录入/网格/排行 | ✅ | ⚠️ 排行只读 | score:entry/manage/view | PC-only 写面 |
| 统计/驾驶舱/六项指标 | ✅ | ✅ 经营分析页 | statistics:view/dashboard:view | ✅(G2 后同口径收口) |
| 系统管理(用户/组织/角色/岗位/配置/日志/权限规则/集团/范围矩阵) | ✅ | ❌ | system:* | PC-only(管理面) |
| 认证(登录/登出/改密/找回) | ✅ | ✅ | 匿名/本人 | ✅ 双端同链路(找回仅PC入口, API 同) |

## 二、API 级实测差异(归一模板后)

- 共同调用 ≈ 49+ 字面重合(模板归一后文件/激励/草稿族均两端同端点);
- **PC-only 真实差异**: score 网格与录入、客户导入导出、公海配置/导入、系统管理族、审计日志、通用 scope 下拉 —— 全部为管理/批量面,移动端无入口,后端 @PreAuthorize 双端一致强制;
- **小程序字面 only 项均为模板拼接形态**,非真实端点差异。

## 三、权限收敛时效(双端)

- 勾选/撤销/改密经 perm_version 即时生效,双端同链路(已实测: 在线旧 token 亦即时收敛);
- 小程序入口隐藏(auth.hasPermission)仅显示层,越权调 API 由后端 403(矩阵用例实测)。

## 四、结论

双端在**共有业务面上 parity 成立**(客户/激励/文件/公海/统计/认证/审批),差异均为"管理面 PC-only"的合理设计而非越权缺口;后端权限码为单一事实源,双端同守卫。此结论补齐审计阶段 7/8 的矩阵文档缺口(此前仅 API 清单无对照)。

> 修订记录 2026-09-22: 初版"审批=小程序无页面"为**采集遗漏**(grep 实采 api/*.js 时 approval.js 存在,页面清单未交叉核对);本次以 app.json 46 页清单+pages/approval 实码复核纠正。教训: 页面级结论必须以 app.json 为准,API 层 grep 不能反推页面有无。
