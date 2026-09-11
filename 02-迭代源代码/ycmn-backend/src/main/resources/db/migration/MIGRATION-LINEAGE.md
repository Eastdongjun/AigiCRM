# 迁移血统说明（V20–V23 分叉已收口）

> 本文件记录 2026-09-11 对迁移链的一次**分叉收口**。改动前请先读完本文。

## 一、曾经发生的问题

生产库与仓库曾出现**两条不同的 V20–V23**：

| 版本 | 生产线（**已部署到生产**） | 仓库线（**从未上生产**） |
|---|---|---|
| V20 | `add_sys_user_column_pref` | `incentive_read_status` |
| V21 | `cleanup_dead_menu_permissions` | `add_finance_view_permission` |
| V22 | `cleanup_dead_menu_permissions_by_code` | `split_file_mutation_permissions` |
| V23 | `file_permission_split_nine_items` | `add_wechat_identity_columns` |

生产的 V20–V23 迁移文件**曾经只存在于生产备份里**（`08-备份索引/09-生产环境备份/deploy/ycmn/flyway-sql/`），仓库中没有。

**后果**：`flyway migrate` 无法把仓库的迁移应用到生产——V20–V23 版本号已被别的脚本占用，会直接冲突。**仓库线当时不具备可部署性。**

## 二、怎么收口的

已核验：**仓库的 V1 与 V12–V19 与生产已应用的版本逐字节一致**（sha256 相同），因此分叉**只在 V20–V23**。

据此做了**单向合并**：

1. 把生产的 V20–V23 **原样收回仓库**，占据 V20–V23（它们是已发布的真实历史，**不得改写**）
2. 仓库开发线整体**后移到 V24–V33**

**版本映射**（旧 → 新）：

| 旧版本 | 新版本 | 说明 |
|---|---|---|
| V20 `incentive_read_status` | **V24** | 新增 `biz_incentive_read` |
| V21 `add_finance_view_permission` | **V25** | 财务明细查看权限 |
| V22 `split_file_mutation_permissions` | **V26** | 拆分文件权限码并继承绑定 |
| V23 `add_wechat_identity_columns` | **V27** | `sys_user.wechat_*` |
| V24 `customer_file_relation` | **V28** | 新增 `biz_customer_file` |
| V25 `customer_protected_date_permission` | **V29** | 客户日期修改权限 |
| V26 `score_menu_and_customer_ranking_menu` | **V30** | 菜单文案 + 客户排行入口 |
| V27 `fix_customer_ranking_menu_parent` | **V31** | 修正客户排行父级 |
| V28 `fix_customer_ranking_menu_parent_from_statistics_route` | **V32** | 同上（另一拓扑） |
| V29 `fix_sys_menu_mojibake_names` | **V33** | 修菜单名乱码 |

## 三、冲突判定：无冲突，是互补

两条线都叫"文件权限拆分"，但**操作对象不同**：

- **生产 V23** 改的是 **`biz_folder_acl.perms`**（文件夹 ACL 的权限字符串，旧 6 项 → 新 9 项）—— 数据层
- **仓库 V26**（原 V22）改的是 **`sys_menu` / `sys_role_menu`**（新增拆分后的菜单权限码并继承 `file:view` 绑定）—— 字典层

因此二者**不冲突**，可线性共存，无需产品决策。

## 四、验证证据

在一次性库上做**全链重放**：

- **23 个迁移全部成功，到 V33**
- **两条线的对象并集均已产出**：生产 V20 的 `sys_user_column_pref` ✅、仓库 V27 的 `sys_user.wechat_openid` ✅、仓库 V28 的 `biz_customer_file` ✅、仓库 V24 的 `biz_incentive_read` ✅
- 表数 52（= 仓库线 51 + 生产 V20 的 1 张表）

## 五、仍未收口的部分：V2–V11 历史缺口

生产存在、而合并链**仍不产出**的表：

`sys_token_blacklist`、`perm_scope_field`、`perm_scope_org`、`perm_scope_shadow_diff`、`biz_sea_config_region`、`biz_sea_config_scope`

它们来自生产更早的迁移线（V2–V11，仓库中已归档在 `sql/migration/archive/`）。仓库的 `V1__baseline.sql` 是 2026-08-11 重建的快照，**并未包含这些表**。

**影响范围**：仅影响**全新库**（开发/测试）——这些表不会被创建，相关功能在全新库上不可用。**不影响生产部署**（生产本就有这些表）。

> **已收口（2026-09-11，V2026.09.11.1）**：缺口已由
> `V2026.09.11.1__restore_v2_v11_gap_tables.sql` 补齐。DDL 逐字取自只读生产备份 dump，机械核对逐字节一致，
> 六张表全部 `CREATE TABLE IF NOT EXISTS`（生产已存在，故可幂等应用、必要时才建）。
>
> 2026-09-11 本地复现确认：用 Flyway 9.22.3 在全新 MySQL 8.0 库上重放本目录全链 —
> 补齐前 **23/23 应用、末版本 V33、52 张表**，`information_schema` 查询确认上述 6 张表**一张都不存在**
> （`missing_gap_tables=0`）；与生产 dump 的表集合做差集，`production∖chain` 恰为这 6 张。
> 补齐后 **24/24、末版本 V2026.09.11.1、58 张表，`production∖chain` 为空集**，
> 反向差异仅 `biz_customer_file` / `biz_incentive_read`（仓库新工作尚未上生产）。缺口属实证而非推测。
>
> `deploy-db.sh` 的 `-ignoreMigrationPatterns='*:missing'` **仍然必需**：生产 `flyway_schema_history`
> 里 V2–V11 已登记，而仓库无对应文件，validate 会报 applied-not-resolved。本迁移是**新建缺失对象**，
> 不改变这一点。

## 六、对使用者的影响

- **旧库（含本地/测试环境）**：flyway 历史里记的是旧版本号语义，**必须重建**，或在 `flyway_schema_history` 中做 `repair` 才与新链对齐。已部署的测试环境 `111.229.37.220` 已按新链重建（52 表 / V33）。
- **生产**：V1–V23 与合并链**完全一致**，因此现在可以正常接收 V24–V33 的部署。
- 改动前的迁移文件备份在中央证据目录：`runs/20260911-deepseek-closure-takeover-001/migration-merge-backup/`。

## 七、以后的约定（2026-09-11 修订：改用日期版本）

### 7.1 命名格式

**已部署的迁移文件不得再改名或改内容**（否则生产校验失败）。`V1`–`V33` 是**冻结的存量版本**，
**新迁移一律用日期版本**，不再沿用递增数字：

```
V<YYYY.MM.DD>.<当日序号>__描述.sql
```

- 例：`V2026.09.11.1__restore_v2_v11_gap_tables.sql`
- 同一天第二个迁移：`V2026.09.11.2__...sql`
- 描述部分用小写蛇形（`[a-z0-9_]`），与存量风格一致。

### 7.2 为什么不能写成 `V2026-09-11-v1`（连字符）

Flyway **9.22.3 实测**（与 `pom.xml` 的 `flyway-core`、`deploy-db.sh` 的镜像版本一致）：
连字符版本号**既不报错也不执行**，只打印一行

```
1 SQL migrations were detected but not run because they did not follow the filename convention.
```

而 `migrate` **仍以退出码 0 结束**。即：迁移"成功"了，活没干 —— 静默空跑。

| 文件名 | Flyway 9.22.3 实测行为 |
|---|---|
| `V2026-09-11-v1__x.sql` | ❌ 检测到但**跳过**，退出码 0（默认 `validateMigrationNaming=false`） |
| `V2026.09.11.1__x.sql` | ✅ 应用，历史记 `2026.09.11.1` |
| `V2026091101__x.sql` | ✅ 应用，历史记 `2026091101` |
| `V20260911120000__x.sql` | ✅ 应用（Flyway 官方时间戳风格） |

**点分是连字符的等价替代**（`.` 是 Flyway 合法的版本分隔符，`-` 不是）。
排序实测无误：`V33 → V2026.09.11.1 → V2026.09.11.2 → V2026.09.12.1`，
日期版本（年份 ≥ 2000）恒定排在存量数字版本之后，不存在乱序应用。

### 7.3 为什么不做存量改名

- `V1`–`V23` **已应用到生产**，改名或改内容会直接让生产 `flyway validate` 失败。
- `V24`–`V33` 的**真实日期无据可查**，倒填日期等于伪造历史。
- 因此采用**分界点切换**（存量数字 / 新增日期），不回溯改名。`V34` 这个号**不再使用**。

### 7.4 三道自动校验（防"静默空跑"复发）

1. **`scripts/check-migration-contract.sh`** —— 已接入 `dev-verify.sh` 本地契约与交付前检查：
   文件名合法 / 版本不重复 / 数字版本必须登记在冻结清单内 / 日期版本年份 ≥ 2000 /
   冻结清单逐字节 sha256 一致 / 目录内无 `._*` AppleDouble。
2. **`db/migration/FROZEN-SHA256.txt`** —— `V1`–`V33` 冻结清单。已部署的迁移改一个字节即 FAIL，
   堵住"改历史迁移"这条最贵的错误路径。
3. **`scripts/deploy-db.sh`** —— `repair` 与 `migrate` 均加 `-validateMigrationNaming=true`（非法文件名
   快速失败、退出码 1）；失败判定补上 `did not follow the filename convention` 与 `No migrations found`；
   上传后断言 `.sql` 数量 > 0，避免"文件没传上去 → flyway 空跑 → 报成功"。

### 7.5 其他

- 任何"从生产收回历史迁移"的动作，都必须**逐字节核对**（sha256）与生产 `flyway_schema_history` 一致后再入库，
  并在 `FROZEN-SHA256.txt` 中**追加**（不得修改已有行）。
