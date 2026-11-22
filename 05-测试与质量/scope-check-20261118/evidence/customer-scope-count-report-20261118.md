# 客户域数据范围核对报告（第一轮·计数级）

> 生成：2026-11-18 ｜ 范围：28 个启用用户（status=1 且 deleted=0）｜ 数据源：测试库 111.229.37.220 只读导出
> 纪律：生产零写入；本轮全部只读；未改动测试库。

## 0. 用户裁决（2026-11-18）

- **TY005 语义已定：office = 全办事处**。当前实际 17 条、配置应见 121 条，**少看 104 条，判定为 P0 业务缺陷**；auto_share 不应再收窄已勾选的 office 范围。
- **测试库暂不修**：不应用 11.17.1，客户条目级（ID 集合）比对挂起；先查其他模块，等后续统一迁移窗口再补。生产零写入。
- 集合级差异（oracle 内部）：仅 uid 41 存在 `config_ids` 比 `final_ids` 多 **104** 条；其余 27 用户配置集合=生效集合（team 用户的 team 集合与直属下级客户集合恰好重合）。

## 1. 结论

1. **计数级核对通过**：28 用户 × 7 个页签 = **196 个计数比对，全部与独立 oracle 一致**（总数 + 成熟创新/突破开拓/铺垫宣传/新建改建扩建大修/预警中/已超期）。
2. **数据条目级（ID 集合）比对被阻断**：测试服 `GET /api/customer/list` 返回 500，原因不是权限，而是**测试库缺列**——Flyway 停在 `2026.11.16.9`，代码已到 `2026.11.17.1`（缺 `name_norm`、`dup_exempt`、`deleted_at`、`deleted_by`、`deleted_reason`、`delete_source`）。计数接口走 `selectCount` 不选实体列，所以正常；列表走 `selectPage` 选实体列，所以 500。
3. 配置与生效语义上发现 **3 个需裁决/跟踪项**（见 §4），其中 TY005 的「office 121 → 实际 17」是当前最明显的配置-生效差。

## 2. 方法与证据

- 数据：测试库只读导出到 `data/`（users / roles / user_roles / scopes / depts / user_org / role_org / org_admin / customers / config / rules / flyway）。
- Oracle：`scripts/scope_oracle.py` **独立实现**，不复用应用层 `resolveScope`；按文档语义计算「配置应见」与「配置+auto_share 应见」。
- 真实 API：本地 JWT（测试服 `JWT_SECRET`）签发 28 个用户 token，调 `GET /api/customer/list/counts-by-filter`。
- 证据：
  - `evidence/scope-count-compare.tsv`（28 用户总数/配置/实际）
  - `evidence/scope-tab-compare.tsv`（28×7 页签逐项）
  - `evidence/scope-count-summary.md`
  - 500 栈：测试服后端日志，支持编号 `S-20261118231215-d1691`；`Unknown column 'name_norm' in 'field list'`；Flyway 最新 `2026.11.16.9`。

## 3. 28 用户结果摘要

| 类别 | 用户 | 结果 |
|---|---|---|
| all（全量） | admin(1), NO001(11) | 294 = 294 |
| self（仅本人） | 38,39,42,45,46,47,49,50,51,52,53,54,55,56,57,58,59,60,63,82 | 配置=生效=实际 |
| team（本人+下级） | TY004(40), TY006(42), NM002(44) | 配置=生效=实际 |
| office（办事处） | TY005(41) | **配置 121，生效 17，实际 17** |
| custom（自定义组织） | NM100(43), TY100(61), TY200(62) | 88/122/84 全一致 |

说明：team 用户配置数与生效数当前相等，是因为其可见直属下级客户集合恰好等于 team 集合；机制上 auto_share 仍在收窄（见 §4-F1）。

## 4. 语义发现（需裁决/跟踪）

### F1（待裁决）TY005：勾选 office，实际只看到 17/121
- 配置：USER 范围行 `office`；锚点由 ROLE 05 精确行取到 `ORG`。
- 配置应见：办事处子树客户 **121**。
- auto_share `{"superior":true,...}` 再追加 `owner_id in (本人+直属下级)`，与 ORG 部门条件取交集 → **17**，与 API 实际一致。
- 问题：如果产品语义是「勾选 office = 看本办事处」，则 104 条客户被 auto_share 收窄，属缺陷；如果语义是「office 只是上限，实际只看本人+直属下级」，则正确。**需 B1 拍板**。历史记录：2026-11-16 审计把 preview 121 改成与列表 17 一致，但那是「两个实现一致」，不等于「配置被兑现」。

### F2（待跟踪）`data_scope_override=team` 对 47/54 未生效
- uid 47 TY008、uid 54 NM006 的 `sys_user.data_scope_override='team'`，但客户域存在 ROLE 通配行 `06 * * self`；`configuredScopeCodes` 优先返回 ROLE 通配 `self`，`dataScopes`（含 override team）被跳过。
- 当前两人无下属，team 与 self 集合相同，所以计数看不出差异；一旦挂下属，会少看下级客户。且 `isTeamScoped` 因 override=team 为真，会跳过系统「上级看下级」开关，可能比普通 role06 用户更窄。
- 建议：受控夹具（给 47 或临时用户挂一个下属）复现，确认是否缺陷；这属于「用户级覆盖角色级」的承诺未兑现。

### F3（待裁决）`auto_share.admin=true` 是死配置
- 配置里有 `admin:true`，但 `getAutoShareOwnerIdsCore` 只实现了 `superior`/`roles`/`users` 三条规则，未消费 `admin`。
- 影响：非 all 用户不会因该开关看到 admin 名下客户。若产品预期「admin 客户自动共享」，则未实现。

### F4（待完成）计数一致 ≠ 集合一致
- 196 个计数全对，只能证明「数量口径」一致；不能证明可见的客户 ID 集合一致（不同集合可能同数）。
- 必须等测试库补列后，用列表接口逐页取 ID 与 oracle 的 expected_ids 做集合比对（extra/missing/字段）。

## 5. 阻断项与处置选项

**P0 环境漂移**：测试服 jar 与测试库 Flyway 不一致（代码 11.17.1，库 11.16.9），客户列表 500，影响所有用户（含 admin）。

处置选项（需用户决定）：
- A. **测试库先 dump 备份，应用 `V2026.11.17.1`，查完按 QC 纪律还原**（推荐；最小、可逆；仅测试库）。
- B. 建独立副本库 + 临时后端（隔离最好，成本高）。
- C. 暂不修，先做其余模块的计数级与静态检查（客户条目级挂起）。

**生产零写入；任何生产动作等用户命令。**
