-- 【2026-11-10 用户令"权限都是勾选的"】撤"授权必须过期"专业默认:
-- 到期时间改为可选——不填=永久(勾选即生效, 删除即收回); 已存授权行全部转永久(NULL)。
-- 判定层本就"NULL=不过期"(FolderPermissionService 过期检查仅对非空判断), 零代码语义改动。
ALTER TABLE biz_folder_type_default MODIFY COLUMN expires_at DATETIME NULL COMMENT '到期时间(可选; NULL=永久, 到期自动收回)';
UPDATE biz_folder_type_default SET expires_at = NULL;
