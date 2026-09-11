# 数据范围与命名规范（AIGICRM）

版本：2026-09-11 ｜ 适用范围：`sys_*` 系统表、权限与数据范围相关字段
**本文件是命名与枚举的单一事实来源（SSOT）**。与代码冲突时以本文件 + `DataScopeType` 枚举为准。

---

## 1. 表命名

| 规则 | 说明 |
|---|---|
| 系统表前缀 `sys_` | `sys_role` / `sys_user` / `sys_menu` / `sys_role_menu` |
| 业务表前缀 `biz_` | `biz_customer` / `biz_incentive_record` / `biz_sea_config` |
| 关联表前缀 `perm_` / `role_` | `role_perm_scope` / `perm_scope_org` / `perm_scope_field` |
| **一律单数** | `sys_role` ✅ ／ `sys_roles` ❌ |
| 快照/备份表 | `<表名>_bak_<YYYYMMDD>`，仅临时保留，须由归档迁移删除 |

## 2. 字段命名

以 `sys_role` 为基准（真实生产结构）：

| 字段 | 类型 | 约束 | 含义 |
|---|---|---|---|
| `id` | bigint | PK, AUTO_INCREMENT | 主键 |
| `group_id` | bigint | NOT NULL DEFAULT 1 | **集团 ID（多租户隔离）** |
| `role_name` | varchar(50) | NOT NULL | 角色名称 |
| `role_code` | varchar(50) | **NOT NULL + UNIQUE** | 角色编码 |
| `description` | varchar(200) | NULL | 描述 |
| `data_scope` | varchar(20) | DEFAULT `'self'` | 数据范围（见 §3） |
| `sort_order` | int | DEFAULT 0 | 排序 |
| `status` | tinyint | DEFAULT 1 | 1 启用 / 0 停用 |
| `created_at` / `updated_at` | datetime | `updated_at` ON UPDATE | 创建/更新时间 |
| `org_id` | bigint | NULL | 归属组织（组织管理员体系） |

**统一约定**：
- 主键一律 `id`；时间一律 `created_at` / `updated_at`（**不用** `create_time` / `update_time`）
- 描述字段用 `description`（**不用** `remark`）；启用标记用 `status`（tinyint，1/0）
- 外键命名 `<被引表>_id`（`group_id` / `org_id` / `role_id` / `user_id`）
- 排序字段用 `sort_order` 或 `sort`

## 3. 数据范围枚举（**权威 = `DataScopeType`，共 7 个值**）

声明序即**宽松度降序**，`rank() = ordinal()`，**数字越小越宽松**；多角色叠加取**最宽松**。

| rank | code | 中文 | 含义 |
|---|---|---|---|
| 0 | `all` | 全部数据 | 本集团全部 |
| 1 | `custom` | 自定义数据 | 按 `sys_role_org` / `perm_scope_org` 配置的组织集合 |
| 2 | `region` | 本大区及以下 | 全部办事处 → 部门 → 人 |
| 3 | `office` | 本办事处及以下 | 全部部门 → 人 |
| 4 | `dept` | 本部门及以下 | 含子部门 |
| 5 | `team` | 本人及下级 | `manager_id` 递归，**限 5 层** |
| 6 | `self` | 仅本人 | — |

### 兼容与废弃值

| code | 状态 | 处理 |
|---|---|---|
| `dept_and_children` | **兼容值** | `OrgScopeResolver` 与 `dept` 同语义；`DataScopeType.fromCode` 已做等价映射（2026-09-11 修复） |
| `org_and_children` | @Deprecated | 枚举仍有常量（主代码有引用），`needChildren()` 仅对它返回 true |
| `org` | @Deprecated | 由 `dept` 兼容替代 |
| 其他未知值 | — | **fail-closed → `SELF`** |

> **注意**：`data_scope` 与 `role_perm_scope.scope_level` **共用上述同一套词表**，不得各自定义。

## 4. `role_code` 命名规范

| 规则 | 说明 |
|---|---|
| 字符集 | 小写字母 + 数字 + 下划线，正则 `^[a-z][a-z0-9_]*$` |
| **禁止纯数字** | `0` / `05` / `06` ❌（无法自解释、易与 id 混淆） |
| `admin` **保留不可改** | 代码 5 处硬编码：`SecurityUtils:107`、`OrgAdminService:117`、`SysUserService:186/419`、`SysRoleService:80-81`、`ScopeService:83` |
| 命名建议 | `admin` / `sales_dir`（销售总监）/ `sales`（销售员）/ `office`（行政）/ `region_mgr`（大区经理） |
| 长度 | ≤ 50（列宽） |
| **改名的连带要求** | 必须**同步** `role_perm_scope.role_code`（生产 72 行）与 `perm_scope_*`；`biz_folder_acl` 存 `roleId` 不受影响 |

## 5. 计算值 vs 持久化字段

| 展示项 | 来源 | 是否入库 |
|---|---|---|
| `menu_count` 菜单绑定数 | `COUNT(sys_role_menu WHERE role_id=?)` | ❌ **不入库** |
| `user_count` 用户数 | `COUNT(sys_user_role WHERE role_id=?)` | ❌ **不入库** |
| 数据范围生效值 | `OrgScopeResolver` 运行时解析 | ❌ 不落库（`sys_role.data_scope` 是兜底声明） |

## 6. 当前偏差与处置

| # | 偏差 | 级别 | 处置 |
|---|---|---|---|
| 1 | `sys_role.data_scope` / `role_perm_scope.scope_level` 的**列注释**遗漏 `custom/region/office/team`，且后者原文乱码 | 中 | ✅ **已修**：`V2026.09.11.2__fix_scope_column_comments.sql`（只改 COMMENT） |
| 2 | `dept_and_children` 在 `OrgScopeResolver` 与 `DataScopeType.fromCode` 之间**口径分裂**（后者静默降级 SELF，并使提权防护被绕过） | 中（当前无数据行使用 → 潜在） | ✅ **已修**：`DataScopeType.fromCode` 增加等价映射 |
| 3 | `role_code` 风格不统一：`admin`（字母）与 `0`/`05`/`06`（数字）并存 | 中 | ⏳ **B 级·升级时执行**（见下） |
| 4 | `role_perm_scope` 存在悬空 `role_code='incentive_view_all'`（`sys_role` 无此角色） | 中 | ⏳ **B 级·升级时清理** |
| 5 | 全库仍有历史遗留 mojibake 注释（生产 58 处 / V1 63 处） | 低 | ⏳ 独立整备迁移，需授权 |

### B 级（生产数据变更，随升级窗口执行）

```sql
-- 1) 角色编码规范化（必须与 role_perm_scope 同事务）
UPDATE sys_role SET role_code='sales_dir' WHERE role_code='0';
UPDATE sys_role SET role_code='office'    WHERE role_code='05';
UPDATE sys_role SET role_code='sales'     WHERE role_code='06';
UPDATE role_perm_scope SET role_code='sales_dir' WHERE role_code='0';
UPDATE role_perm_scope SET role_code='office'    WHERE role_code='05';
UPDATE role_perm_scope SET role_code='sales'     WHERE role_code='06';
-- 2) 清理悬空范围配置
DELETE FROM role_perm_scope WHERE role_code='incentive_view_all';
-- 3) 校验
SELECT role_code, COUNT(*) FROM sys_role GROUP BY role_code;
SELECT DISTINCT role_code FROM role_perm_scope ORDER BY 1;
```
> 注意：`admin` **不得**出现在任何改名语句中（见 §4）。
