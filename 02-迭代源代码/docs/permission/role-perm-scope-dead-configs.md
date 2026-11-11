# role_perm_scope 死配置登记册（裁决B：标记停用+观察）

> 快照时点: 2026-11-12 生产只读（31 组 / 89 行） | 判定权威: `scripts/check-scope-axis-contract.sh` 动作轴声明表(源码消费锚) + `OrgScopeResolver` 匹配语义
> 裁决: 2026-11-12 用户拍板 B→A 分两步——本册为 B(标记停用+登记观察, **零迁移零行为变化**); A(迁移删行)挂观察期后待令。
> 关联: design.md 已知问题#2 | 先例: file 域死配置已由 V2026.11.09.1 清理。

## 一、判定方法（三判据）

1. **源码零消费**: 后端对 (module,action) 的消费点全部登记在动作轴声明表（`.module()`/`.action()` 链式与 `resolveModuleUsers[WithinGroup]` 位置参数双扫描）。声明表之外的档位=后端永不查询。
2. **空 role_code 永不匹配**: `role_perm_scope.role_code` 为空的行在按用户角色解析时永不命中（`OrgScopeResolver` 按角色码等值匹配）。
3. **通配语义勿误判**: `module='*'` 的行会被**任意** (module,action) 查询命中（`"*".equals(cMod)`）——`*/*` 是全局兜底范围配置，**不是死配置**；仅其中空 role_code 的行判死。

## 二、活档清单（勿动，13 档 + 通配活行）

| module | action | 消费锚（源码） | 生产配置行 |
|---|---|---|---|
| customer | view | 客户列表/详情/导出/统计基础口径 | 4 行(空,0,05,admin) |
| customer | edit | canAccessCustomer(x,"edit") | 4 行 |
| customer | delete | canAccessCustomer(x,"delete") | 4 行 |
| customer | finance_edit | canAccessCustomer(x,"finance_edit") | 2 行(0,05) |
| file | view/manage/delete | 文件组织矩阵（FileSpaceDefaults/矩阵判定链） | 各 4 行 |
| statistics | view | resolveModuleUsers(x,"statistics") 统计/排行可见集 | 1 行(0) |
| sea | view | resolveModuleUsers(x,"sea") 公海列表可见集 | 3 行(0,05,06) |
| incentive | stats_view | resolveModuleUsers(x,"incentive","stats_view") 四项激励数据范围 | 4 行 |
| task | view | resolveModuleUsersWithinGroup(x,"task") | （无行，配置面可新增） |
| score | view | resolveModuleUsersWithinGroup(x,"score") | 2 行(0,admin) |
| system | view | resolveModuleUsersWithinGroup(x,"system") | （无行） |
| `*` | `*` | 全档兜底匹配 | **05/06 两行活兜底——勿动**；另 7 行空 role_code 判死 |

## 三、死档登记（19 组，判停用观察）

| # | module | action | 生产行数(role_code) | 判死依据 |
|---|---|---|---|---|
| 1 | customer | add | 4(空,0,05,admin) | 声明表无 customer\|add 消费锚 |
| 2 | customer | date | 3(0,05,admin) | 同上（customer:date:edit 走 sys_menu 码不走 scope） |
| 3 | customer | export | 4(空,0,05,admin) | 导出范围实际由 customer\|view 档承载 |
| 4 | customer | import | 4(空,0,05,admin) | 导入无范围档消费 |
| 5 | customer | share | 1(空) | 零消费 + 空 role_code 双死 |
| 6 | dashboard | view | 3(0,05,06) | dashboard 模块零消费（统计可见性走 statistics\|view；2026-11-12 裁决A后 dashboard:view 码亦退役） |
| 7-13 | incentive | add/delete/edit/manage/view/view-all/view-shared | 3/2/2/2/3/3/3 | 激励可见性实际走 biz_incentive_grant 授权表（按人核定），7 档全无消费锚 |
| 14-16 | score | entry/manage/ranking | 1/1/2 | 学分域仅 view 档消费（录入/管理/排行走 sys_menu 码） |
| 17-18 | sea | config/pool | 1/1 | 公海配置走 sys_menu 码；池级编辑走池名单不入 scope |
| 19 | system | dept | 1(0) | system 域仅 view 档消费 |
| 附 | `*` | `*`（空 role_code） | 7 行 | 空 role_code 永不匹配（≠通配活行） |

## 四、观察判据与 A 步（删行）草案

**观察判据**（B 期间，任一触发即复核本册）:
- PermCenter 配置面/审计日志出现上述死档的新增勾选或变更（说明出现新消费预期，须先补声明表再谈清理）;
- 后端源码新增 `.module()/.action()` 或 `resolveModuleUsers` 消费点（动作轴契约①会拦：未声明即红）;
- 生产行为抽样不变：四角色各域可见集与本册快照口径一致。

**UI/写入面防护（T7 2026-11-12 已上）**:
- `RolePermScopeService.updateScopes` 写入校验：`(module,action)` ∉ 15 档消费集 → 400"该档位后端未消费，禁止配置"——死档**新增**已被机器拦截（存量行不受影响, 清理仍走 A 步）;
- 消费集与 check-scope-axis-contract.sh 声明表**契约④双向对账**（两处同源漂移即红, 负例已自证）。

**A 步草案**（观察期后待令执行，迁移要求）:
- 按 (module,action) 自然键 DELETE（含空 role_code 通配死行），分域小步提交（incentive 域先行——占死行最多且语义最明确）;
- 删行前后各拉一次 `GROUP BY module,action` 对账，**只允许登记册内行消失**;
- 删行迁移入库后跑 check-scope-axis-contract ③（迁移链写入面仍 ⊆ 声明表枚举）。

**风险注记**: 死档行对运行时零影响（永不匹配），清理属数据治理卫生——A 步无行为收益，仅消除配置面误导（管理员可能误以为勾了生效）。
