# 需求规格

## 目标用户与场景

- 角色：销售员（06）、行政（05）等非管理员业务角色，日常高频使用客户管理（新增/列表/详情）、四项激励、学分、公海等页面。
- 触发场景：任何一次打开上述页面（页面加载即拉取字典/部门下拉数据）。
- 目标：这些角色打开业务页面时，字典/部门下拉数据正常加载，不再弹「没有访问权限，请联系管理员授权」、不再出现空下拉。

## 事实

- NM005（王磊磊，销售员 06，role_id=11）打开 `/customer/add` 实测弹「没有访问权限」，3 个 API 403（depts / dict/data/industry / dict/data/dev_type）。
- 后端 `SysDictController`/`SysDeptController` 的 GET 只读接口要求 `system:config:view` / `system:dept:view`；生产库实测行政(05)、销售员(06) 未绑定这两个权限码，admin、销售总监(0) 已绑定（来源：生产库 sys_role_menu 查询，2026-08-04）。
- 前端 `getDepts/getDictData` 被 7 个业务页面调用（来源：ycmn-ui/src 源码 grep）。
- 用户已确认修复方案 B：后端只读接口放宽为登录即可读（`isAuthenticated()`）。

## 假设

- 字典数据（行业/开发类型/学分分类等）和部门树为业务表单引用的基础数据，系统已设计隐藏权限项「基础数据-部门/字典/用户」（sys_menu 98/99/100，visible=0）作为授权载体，权限码与后端接口一致，勾选即生效——采用该既有机制，**不改接口鉴权代码**。
- 用户规则："每个角色勾选了就能操作，没有勾选就不能操作" → 保持严格授权语义，未勾选角色访问仍 403。
- 给销售员/行政勾选 98/99 不产生菜单显示（visible=0），侧边栏无变化。
- 除已识别的 7 个页面外，不存在其他依赖这些接口的受限行为（实现阶段用影响分析核对）。
- 统计数据分析的数据范围（REQ-006/007）为本 FEAT 独立追加需求，与勾选修复并行交付。

## 需求就绪检查(进入开发前逐项勾选,缺任一项不得进入 PLAN_READY)

- [x] 业务目标明确(为什么做)
- [x] 使用对象明确(谁用)
- [x] 当前/目标流程明确(现状与目标)
- [x] 功能范围明确(做什么/不做什么)
- [x] 页面原型或界面描述完成（非 UI 改动：页面表现=下拉数据可用、无弹窗，验收即可观察）
- [x] 字段清单完整(新增/修改字段：无字段变更)
- [x] 权限规则明确(谁能操作/数据范围)
- [x] 异常场景明确(空/错/重复/并发/超时)
- [x] 验收标准可测试(AC 可执行)
- [x] 涉及数据/接口/文档影响已识别

## 需求确认

- 用户已确认（2026-08-04，AskUserQuestion 选择"方案 B: 后端接口改登录即可读"）。

## 阻断问题

- 无。

## 功能需求

| 需求 ID | 描述 | 来源 | 优先级 |
|---|---|---|---|
| REQ-001 | 任意登录用户可读取部门树 `GET /api/system/depts`、`GET /api/system/depts/tree`（接口改为 `isAuthenticated()`），新增客户等页面的所属大区/办事处下拉可加载 | 用户最终确认方案 B"登录即可读"（2026-08-04 二次确认） | Must |
| REQ-002 | 任意登录用户可读取字典数据 `GET /api/system/dict/data/{dictCode}`、`GET /api/system/dict/data/type/{typeId}`（接口改为 `isAuthenticated()`），行业/开发类型等下拉可加载 | 同上 | Must |
| REQ-003 | 字典与部门接口的**写操作**（POST/PUT/DELETE）权限保持不变，仍要求 `system:config:edit` / `system:dept:edit`；未登录访问仍被拒绝；其他系统管理接口（users/roles/logs 等）权限不变 | 权限边界约束 | Must |
| REQ-004 | 已勾选的「基础数据-部门/字典」隐藏权限（sys_menu 98/99，visible=0）保留绑定（行政/销售员），作为权限体系既有能力，与放开接口并存无冲突 | 权限体系完整性 | Should |
| REQ-005 | 修复在生产环境 www.aigicrm.com 生效，NM005 生产实测通过 | 用户报障目标 | Must |
| REQ-006 | 统计分析页（/statistics，接口 `GET /api/statistics/analysis`）数据按角色数据范围过滤：销售员(self)只统计自己名下客户的数据，上级角色(dept_and_children 等)统计本部门及下级部门数据，管理员(all)保持全量 | 用户 2026-08-04 补充规则"统计数据分析里面数据是个人的才可以；上一级的能看到下一级别的数据" | Must |
| REQ-007 | 日报/周报（`dailyStats`/`weeklyStats`）的文件统计与经营分析的文件统计按同一数据范围过滤（销售员只统计自己上传的文件） | 与 REQ-006 同口径，用户补充规则 | Must |

## 业务规则与权限

- 字典/部门只读：`isAuthenticated()`（登录即可读）。
- 字典/部门写操作：`system:config:edit` / `system:dept:edit`（不变）。
- 其余系统管理接口：不变。
- 数据范围：不涉及行级数据过滤（接口返回全量字典/部门树，与现状一致）。

## 异常与边界

- 未登录/无效 token 访问只读接口 → 401（不变，仍拒绝）。
- 下拉数据为空（字典无数据）：页面保持空下拉，不弹权限错误（现有前端行为）。
- 接口路径与请求/响应结构零变化，前端无需改动。

## 非功能需求

- 性能：仅放宽鉴权判断，无额外开销。
- 安全：不暴露任何写接口；字典/部门树为共享引用数据。
- 兼容：Spring Security `isAuthenticated()` 与现权限体系兼容。
- 可观测：保持现有日志。
- 回滚：git 还原 2 个文件重新构建部署即可恢复。

## 验收标准

|验收 ID | 对应需求 | Given / When / Then |
|---|---|---|
| AC-001 | REQ-001 | Given NM005（销售员）登录生产/测试环境 When 访问 `/customer/add` Then 页面无「没有访问权限」弹窗 |
| AC-002 | REQ-001 | Given NM005 登录 When 打开新增客户页 Then 「所属大区」下拉可选到部门数据 |
| AC-003 | REQ-002 | Given NM005 登录 When 打开新增客户页 Then 「行业」「开发类型」下拉可选到字典数据 |
| AC-004 | REQ-001/REQ-002 | Given NM005 登录 When 访问客户列表/客户详情/四项激励/公海页 Then 无新增 403 报错，行业等下拉正常 |
| AC-005 | REQ-003 | Given 非管理员（NM005）登录 When POST `/api/system/dict/data` 或 POST `/api/system/depts` Then 返回 403 |
| AC-006 | REQ-004 | Given 非管理员（NM005）登录 When GET `/api/system/users`（或 roles/logs） Then 返回 403 |
| AC-007 | REQ-004 | Given 无有效 token When GET `/api/system/dict/data/industry` Then 返回 401 |
| AC-008 | REQ-001~004 | Given 管理员登录 When 访问系统管理页（组织架构/系统设置）及新增客户页 Then 功能完全正常（无回归） |
| AC-009 | REQ-005 | Given 生产部署完成后 When 用 NM005 在 www.aigicrm.com 实测新增客户页 Then 无弹窗、下拉正常、可完成一次客户新增 |
| AC-010 | REQ-006 | Given NM005（self）登录 When 打开统计分析页（/statistics） Then 客户总数/月度新增/行业分布/办事处对比只含其名下客户，不含他人客户数据 |
| AC-011 | REQ-006 | Given 行政（05，dept_and_children）账号 When 打开统计分析页 Then 数据覆盖本部门及下级部门，不含其他大区数据 |
| AC-012 | REQ-006 | Given 管理员（admin，all）登录 When 打开统计分析页 Then 数据与修复前一致（全量，无回归） |
| AC-013 | REQ-007 | Given NM005（self）登录 When 查看日报/周报文件统计与经营分析文件统计 Then 只含本人上传的文件数 |
| AC-014 | REQ-003 | Given 非管理员登录 When POST `/api/system/depts` 或 POST `/api/system/dict/types` Then 返回 403（写操作严格授权未放开） |

## 追踪关系

- REQ-001 → AC-001, AC-002, AC-004
- REQ-002 → AC-003, AC-004
- REQ-003 → AC-005, AC-006, AC-007, AC-008, AC-014
- REQ-004 → AC-008
- REQ-005 → AC-009
- REQ-006 → AC-010, AC-011, AC-012
- REQ-007 → AC-013
