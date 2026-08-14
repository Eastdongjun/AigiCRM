# 20260806 — 角色/岗位集团边界（统一权限体系 §4.3 遗留①）

## 改动内容

- `sys_role` 新增 `group_id BIGINT NOT NULL DEFAULT 1`（集团归属，默认主集团 1）+ `idx_role_group` 索引
- `sys_position` 新增 `group_id BIGINT NOT NULL DEFAULT 1` + `idx_position_group` 索引
- 后端 `SysRoleService.list()` / `SysPositionService.list()` 按集团边界过滤：
  - 平台超管（admin 角色）：全量（跨集团管理/审计，现有机制）
  - 其他用户：仅见本集团（`group_id ∈ 当前用户集团集合`；取不到集团归属时退主集团 1，fail-closed）
  - 口径与 `SysUserService.applyGroupScope()`（人员列表）一致，公共入口 `GroupScopeUtil.visibleGroupIds()`

## 执行顺序（部署）

1. 测试库执行迁移脚本（幂等，可重复执行）：
   ```bash
   mysql -h <host> -P <port> -u root -p --default-character-set=utf8mb4 <库名> < group-id-for-role-position.sql
   ```
2. 后端发版（重新编译部署）后，用非 admin 角色登录验证角色/岗位管理页仅显示本集团数据。
3. 确认无误后生产执行同一脚本 + 后端部署（走既有 `scripts/deploy-db.sh` / `scripts/deploy-backend.sh`）。

## 验证

- 单元测试：`SysRoleServiceTest` / `SysPositionServiceTest` 各 3 个集团边界用例（非 admin 过滤 / admin 全量 / 空集团退主集团 1）。
- 迁移脚本幂等：重复执行返回 `SELECT 1` 跳过，不报错。

## 回滚

- 代码：`git revert` 本次提交。
- 数据库：脚本注释内附 `DROP COLUMN group_id`（回滚前先备份）。
