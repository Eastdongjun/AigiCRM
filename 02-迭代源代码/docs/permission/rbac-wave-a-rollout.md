# RBAC 收敛 + 安全 P0 批次（Wave A）发布手册

> 2026-08-14。发布顺序是安全红线：**M1 SQL 必须先于应用代码上线**——否则
> `UserDetailsServiceImpl` 读三新列（pwd_changed_at/perm_version/pwd_reset_required）会抛
> BadSqlGrammar → JwtAuthFilter catch 后全站 401；`blacklistToken` 写 `sys_token_blacklist`
> 会 500 导致注销不可用。

## 发布顺序（每环境都按此执行）

1. **备份**：`mysqldump --single-transaction ycmn > backup-pre-wave-a-$(date +%Y%m%d-%H%M).sql`
   （或至少 dump 5 表：sys_menu / sys_role / sys_role_menu / sys_user_role / biz_incentive_permission）
2. **执行 M1**：`mysql -uroot -p ycmn < sql/migration/20260814/rbac-convergence-m1.sql`
   （幂等，可重复执行；两轮执行第二轮应仅输出守卫 `SELECT 1`）
3. **执行 M1 验证查询**（脚本末尾验证段）：
   - 4 个新权限码存在（incentive:manage/view-all/view-shared/file:acl:manage）
   - 关键绑定：`incentive_view_shared → incentive:view-shared`（缺此绑定=全员分享可见性错误收窄）
   - 全员绑定「激励-分享可见」数量 = 活跃用户数
   - 销售员(11) 的 incentive:edit/delete 残留 = 0
   - sys_user 三列存在、sys_token_blacklist 表存在
   - pwd_reset_required=1 名单 = 仍在使用默认口令哈希的账号
4. **部署后端 jar**（常规流程）+ **前端 dist**
5. **冒烟**：登录/注销/改密/激励列表/客户编辑五条链路 + 权限三链验证
6. **回滚**：`mysql ycmn < sql/migration/20260814/rbac-convergence-m1-rollback.sql` + 回滚 jar

## 已知部署影响（用户可见）

- 仍在使用默认口令（123456）的账号会被强制首登改密（随机临时口令由管理员转交）。
- 销售员(11) 失去激励分类管理/批量重算能力（按 20260805 设计矩阵补齐，用户已拍板）。
- 授权面板"撤销"语义 = 收回"看全部"，保留"看已分享"（回到基础可见范围）。

## 残留风险（接受并记录）

- Redis 与 DB 同时不可用时，黑名单判定仍 fail-open（有 error 日志）；改密撤销由 DB
  pwd_changed_at 兜底不受影响。
- Redis 正常时的黑名单快速路径仍保留（命中免 DB 查询）。
- incentive:manage 授权变更依赖角色管理（evict+版本自愈双保险，最长残留 ≤ 1 个请求）。
