# §8 Batch 0 报告：customer:view 新库期望集（2026-11-18）

- 数据集：`aigicrm_prod_20261118`（生产 2026-11-18 快照；dump md5 `2936f109…`）
- 方法：复用 `scope-check-20261118/scripts/scope_oracle.py` 的独立期望集逻辑（只读 SQL 导出，不调用应用层 `resolveScope`）；已去掉 JWT/API 依赖；API 实见集合待 W2 运行时窗口。
- 用户：总数 69，**活跃 28**（status=1,deleted=0）；客户基数 295（非删/tenant=1/非 sea/archived）；角色 admin 1 / 0×1 / 06×23 / 05×3。

## 一、结果

| 项 | 数值 |
|---|---|
| 活跃用户 × 轴 | 28 × `customer:view` |
| 期望集计算错误 | 0 |
| final_count=0 用户 | 0 |
| config=final | 27 人 |
| **config≠final** | **1 人：uid 41 TY005，config 121 / final 17 / 少 104（note=narrowed）** |
| auto_share 分布 | no-share 22 / narrowed 4 / all-no-config 2 |
| 锚点分布 | OWNER 25 / ORG 3 |
| 范围类型（assignee） | ROLE 20 / USER 8 |

## 二、DEF-1 复现（P0 业务缺陷）

- uid 41 `TY005`，USER/office 显式授权，配置应见 **121** 客户；经 `apply_auto_share` 后最终应见 **17**，**少 104**。
- 与 2026-11-18 旧库审计（DEF-1）完全一致——**缺陷在新生产快照中仍存在**，不是旧库/旧代码问题。
- 用户已裁决：**office = 全办事处，auto_share 不应再收窄已勾选范围**（登记册 §9 DEF-1）。
- 工单：`../tickets/DEF-1-TY005.md`（实施方修 `resolveScope/applyAutoShare`；QC 复验）。
- 影响：TY005 实际少看 104 个客户，属 P0 业务缺陷（missing>0）。

## 三、范围类型覆盖（§8.1）

- 已出现：`all` / `self` / `team` / `office` / `custom`
- **未出现（需夹具/待核）**：`dept`、`dept_and_children`、`org`、`org_and_children`、`region`、`none`、`field`、`group`、`legal_entity`
- 结论：现有生产数据**不足以验证 9 种范围类型**；Batch 2 必须在专用库副本造夹具，不能把"没数据"写成"通过"。

## 四、证据

- `data/*.tsv`：新库只读导出（users/roles/user_roles/scopes/depts/user_org/role_org/org_admin/customers/config）
- `scripts/scope_oracle.py`：打补丁后的 oracle（SECRET 占位，无 API 调用）
- `expected-customer-view.tsv`：28 活跃用户逐人期望计数（含 7 页签）
- `expected-ids.tsv`：逐人 final ID 集合（供 W2 API 实见比对）
- `summary.md`：机器生成摘要

## 五、限制

- 仅 `customer:view` 一个轴；其余 17 轴未做。
- API 实见集合未取（后端仍连基线库 `aigicrm_prod_20261016`）；需 W2 运行时窗口。
- `sys_user_role.expire_at` 未过滤（oracle 旧逻辑）；如存在过期角色，需在 Batch 1 前补。
- 组合语义（override/auto_share/role custom）B1 未拍板；本报告按单一机制出期望，不把"口径未定"写成通过。

## 六、下一步

1. W2 窗口申请已发协调方（四要素）——后端临时指向新库，做 28 用户 API 实见集合比对。
2. Batch 2：专用副本 + 12 夹具用户覆盖 9 种缺失范围类型（需窗口/授权）。
3. Batch 4：生产只读对比用户数/最大 id/配置行数，确认快照与生产一致。

## 七、18 轴配置矩阵（Batch 0 扩展）

- 28 活跃用户 × 18 轴：**none=0**（全部有 explicit 或 default 授权）。
- 走角色默认兜底（非 explicit）：`customer:finance_edit` 1 人、`approval:view` 2 人、`reminder:view` 2 人、`sea:view` 1 人、`task:view` 2 人、`system:view` 2 人；其余轴 28 人全 explicit。
- 矩阵文件：`user-axis-matrix.tsv`；统计：`user-axis-summary.md`。
- **配置面 ≠ 生效面**：DEF-1 已证配置 121 与生效 17 差 104；本矩阵只描述"谁被勾了什么"，不判通过。

## 八、生产 vs 新快照（只读对比）

| 项 | 生产 | 新快照 |
|---|---|---|
| sys_user 行数 / MAX(id) | 69 / 82 | 69 / 82 |
| biz_customer | 625 | 625 |
| role_perm_scope | 104 | 104 |
| sys_config | 23 | 23 |

- 完全一致：新快照 = 生产 2026-11-18 16:37 UTC 的完整副本；"全部数据"边界 = 快照时点。
- Flyway 对比应按 `installed_rank` 取最新（`2026.11.19.2`）；`MAX(version)` 是字符串比较，返回 `9` 不是有效指标（本表未采用）。
- 生产 16:37 之后若有新用户/新数据，不在本快照内；W2 Batch 4 会按需复核。
