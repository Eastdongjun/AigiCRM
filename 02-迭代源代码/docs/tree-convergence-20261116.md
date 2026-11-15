# 源码树与测试工作树收敛记录（2026-11-16）

> 范围：仅测试环境 `http://111.229.37.220`；未触碰生产。
> 结论：两棵树已在集成分支收敛为同一份内容，后端 1,099 tests / 前端 324 tests / UI build / 小程序 92 JS 语法检查全部通过。
> 集成分支：`integration/perm-fabushi-20261116`（正式源码仓库）；测试工作树同名分支 `integration/perm-fabushi-20261116`。

## 1. 收敛前两棵树的真实状态

| 项目 | 正式源码树 | 测试工作树 |
|---|---|---|
| 仓库根 | `/Users/dongjun/东来/项目/02-已上线/01-AIGICRM`（`02-迭代源代码` 为子目录） | `/private/tmp/aigicrm-fix-20261114`（独立仓库） |
| 分支 / HEAD | `feature/perm-kernel` / `dd2a8315` | `main` / `1134dae` |
| 历史关系 | 1192 个提交 | 42 个提交；首提交 `94ffd26 baseline e8161848 copy` 是源码 `e8161848` 的内容快照，之后权限底座独立演进 |
| 未提交改动 | 124 项（后端 9 / 小程序 60+ / 前端 3 / 文档 3） | 后端 V15 权限修复 + 法布施未提交（部署基线） |

### 1.1 迁移差异（确定性差异）

测试工作树比源码树多 13 个已部署迁移，源码树一个都不缺地没有：

```
V2026.11.14.4__file_version_cleanup_index.sql
V2026.11.14.5__admin_minimal_bootstrap.sql
V2026.11.14.6__system_task_run_code.sql
V2026.11.14.7__approval_transfer_code.sql
V2026.11.14.8__reminder_ensure_code.sql
V2026.11.14.9__admin_incentive_grant_bootstrap.sql
V2026.11.14.10__admin_management_codes.sql
V2026.11.14.11__file_permission_v2.sql
V2026.11.15.1__permission_field_codes_and_group_visibility.sql
V2026.11.15.2__admin_only_codes_cleanup.sql
V2026.11.15.3__sys_permission_meta_registry.sql
V2026.11.15.4__recycle_admin_only.sql
V2026.11.15.5__admin_all_active_menus.sql
```

法布施 3 个迁移 `V2026.11.16.1/2/3` 两树内容完全一致（`diff` 空）。测试库 Flyway 已到 `2026.11.16.3`。

### 1.2 后端差异（内容级）

- 测试树独有（V15 权限底座，源码树没有）：
  - `common/auth/PermissionDependencyRules.java`
  - `common/tx/AfterCommit.java`
  - `module/customer/service/FieldPermissionService.java`
  - `module/system/service/PermissionMetadataService.java`
  - `AuthorizationService` 去掉 admin 旁路（`Admin 也必须实际持有权限码`，见 `AuthorizationService.java:57`）
  - `SysUserService` 的 admin 旁路改为显式码 `system:role:grant`（`SysUserService.java:289/556/778`）
- 源码树独有（法布施，测试树为未提交同内容）：
  - `module/fabushi/*`、`common/security/PasswordPolicy.java`、V2026.11.16.x
- 真正需要三方合并的文件只有 5 个：
  - `FileService.java`：测试树有 11-14 版本清理/引用计数修复；源码树有 `fabushi:create` 上传门禁与 `fabushi:view` 下载旁路
  - `RolePermScopeService.java`：测试树有 P1-4/P1-5 保存校验与行锁；源码树有 `fabushi|stats_view` 范围档
  - `PermCenterService.java`：源码树有 `{fabushi, stats_view}` 范围目标
  - `PermissionBehaviorMatrixTest.java` / `PermissionMatrixTest.java`：两边各自新增法布施/权限矩阵用例
- 合并方式：`git merge-file` 三方合并（base=源码 `e8161848`，ours=测试树，theirs=源码树），5 个文件均以 exit 0 干净合并；`MybatisPlusConfig` 仅注释冲突，取源码树注释。

### 1.3 小程序差异

源码树是超集：权限刷新（`utils/permission-refresh.js`、`X-Perm-Epoch`、403 自愈、TabBar 置灰、诊断页）+ 法布施 5 页。测试树小程序为旧版。收敛时整目录覆盖，收敛后两树 `diff` 仅差 `.git` 与 `.mimosa` 噪声。

### 1.4 PC 差异

测试树是超集（权限中心 meta 驱动、`PositionRulePanel.vue`、V15 码门禁）。源码树独有的是法布施三页（两树内容一致）与权限中心法布施分组/范围目标。收敛时以测试树为基线，补：

- `aigicrm-ui/src/composables/usePermissionData.js:44` 法布施分组、`:59` 前缀映射
- `aigicrm-ui/src/views/system/PermScopeManager.vue:193` `fabushi:stats:view → (fabushi, stats_view)`
- PC 密码策略文案对齐 8–30 位 + 字母 + 数字（`layout/index.vue:304`、`ForgotPasswordDialog.vue:73/144`）
- 重新生成双端权限码常量（90 码）与 API 清单（264 接口）

### 1.5 测试差异

| 口径 | 收敛前源码树 | 收敛前测试树 | 收敛后 |
|---|---|---|---|
| `@Test` 注解数 | 1,072 | 1,094 | 1,105 |
| 测试文件数 | 100 | 106 | 106 |
| 实际执行（mvn test） | 本轮未跑 | 交接记录 1,088 | **1,099 / 0 failures / 0 errors / 0 skipped** |

收敛后多出的用例主要来自两树矩阵合并（法布施已读/木鱼/评论 + V15 字段权限/组织管理员/回收站），不是放宽断言。

## 2. 收敛后验证（正式源码树）

| 门禁 | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 后端全量 | `JAVA_HOME=...21 mvn -o -Dmaven.repo.local=/tmp/m2repo/repository test` | 0 | PASS，1,099 tests / 0 failures |
| 后端编译 | `mvn -o -DskipTests compile` | 0 | PASS |
| 前端单测 | `cd aigicrm-ui && npm test` | 0 | PASS，45 files / 324 tests |
| 前端构建 | `npm run build` | 0 | PASS（dist 产出） |
| 小程序语法 | 92 个 `.js` 逐个 `node --check` | 0 | PASS |
| 生成物检查 | `bash scripts/check-permission-codes-generated.sh` | 0 | PASS（90 码，PC/MP 一致） |
| API 清单新鲜度 | `bash scripts/check-api-list-fresh.sh` | 0 | PASS |
| 权限绑定覆盖 | `bash scripts/check-permission-binding-coverage.sh` | 0 | PASS（91 码） |
| 权限码消费 | `bash scripts/check-permission-code-consumption.sh` | 0 | PASS（PC+MP 68 码） |
| 范围目标对账 | `bash scripts/check-perm-scope-targets-parity.sh` | 0 | PASS（12 对） |
| 范围轴契约 | `bash scripts/check-scope-axis-contract.sh` | 0 | PASS（15 档） |
| 动作门禁覆盖 | `python3 scripts/check-action-gate-coverage.py` | 0 | PASS（PC 77 + MP 20） |
| 字典来源契约 | `bash scripts/check-dict-source-contract.sh` | 0 | PASS |
| 空白字符 | `git diff --check` | 0 | PASS |

## 3. 合并决策（为什么不是简单二选一）

1. 以测试工作树为“已部署基线”：V15.1–V15.5 迁移、`sys_permission_meta`、字段权限、admin-only 码已在测试库生效，不能回退。
2. 小程序整目录取源码树：测试树小程序没有权限刷新与法布施内容流，直接取会回退已完成的 MP 权限修复。
3. `FileService` 不能整文件二选一：两边分别修了不同缺陷（版本字节删除事务性 vs 法布施空间门禁），必须三方合并。
4. `SysUserService` 取测试树语义：源码树的 `isAdmin() return` 会绕过 V15 显式授权模型；测试树用 `system:role:grant` 显式码，与“admin 通过迁移持有全部菜单”的新口径一致。
5. 生成物不手写：权限码由 `gen-permission-codes.py` 从合并后后端重生成；API 清单由 `gen-api.py` 重生成，避免码表/接口漂移。

## 4. 仍未收敛/未验证

- `docs/unified-feature-audit.md` 两树差异约 393 行、`scripts/miniprogram/verify-*.mjs` 仍有版本差；不影响构建与权限门禁，但下次文档门禁前需再对齐。
- 小程序 UI 真机/DevTools 验收仍为 BLOCKED（Automator 端口问题），见 `docs/fabushi-implementation-20261116.md` 的 MP 验收段。
- 生产未做任何部署；生产前必须预建 `/app/uploads/fabushi` 并授权、执行 Flyway 到 `2026.11.16.3`、跑双口径迁移。

## 5. 回滚

- 代码：`git revert` 集成分支两个提交或在源码树 `git checkout feature/perm-kernel` 回到 `dd2a8315`。
- 测试库：保留迁移前备份 `/tmp/pre-fabushi-20261116-002509.sql.gz`（测试服）；V15 迁移只增表/码/绑定，无删除业务数据。
- 生产：未执行，无回滚对象。
