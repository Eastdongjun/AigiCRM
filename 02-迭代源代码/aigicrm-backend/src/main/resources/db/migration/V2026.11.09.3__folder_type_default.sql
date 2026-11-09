-- 【2026-11-09 类型级默认权限】文件空间(公共/个人)默认权限表
-- 需求: 管理员按 角色×空间类型×动作(建夹/上传/编辑/删/分享/下载/查看) 配置默认权限;
--       新建文件夹自动继承该类型默认 ACL(物化进 biz_folder_acl, 可逐夹微调),
--       根级公共建夹门槛从"仅管理员硬编码"改为"管理员 ∨ 类型默认含 create"。
-- perms 词表 = 现行 6 短名(view/upload/edit/delete/share/download) + create(可建该类型根夹)。
-- 不 seed: 空表 = 现状行为零变化(公共根=admin 专属, 新夹无继承), 管理员在权限中心配置。
-- tenant_id 列随 biz_folder_acl 同款(DB 默认 1, 实体不映射)。
CREATE TABLE biz_folder_type_default (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    folder_type VARCHAR(20) NOT NULL COMMENT '空间类型: public|private',
    role_id     BIGINT       NOT NULL COMMENT '角色 id (sys_role.id)',
    perms       VARCHAR(500) NOT NULL COMMENT '权限 CSV: view,upload,edit,delete,share,download,create',
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    UNIQUE KEY uk_type_role (folder_type, role_id),
    KEY idx_type (folder_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件空间类型默认权限(类型级默认+逐夹覆盖)';
