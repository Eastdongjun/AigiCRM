-- 【2026-11-09 清理】文件模块 role_perm_scope 死配置 6 行摘除
-- 背景: 2026-11-03 范围对账已裁决"文件=文件夹权限"(FileService/FolderPermissionService
-- 均不消费 role_perm_scope 的 file 域行), 权限中心白名单同步摘除; 但库里 2026-08 的
-- 6 行旧配置未清, 成休眠数据——若未来文件模块接组织范围, 这些旧值会突然生效造成权限跳变
-- (变更八法·不写休眠代码)。此迁移按 (module='file') 精确摘除, 幂等可重跑。
DELETE FROM role_perm_scope WHERE module = 'file';
