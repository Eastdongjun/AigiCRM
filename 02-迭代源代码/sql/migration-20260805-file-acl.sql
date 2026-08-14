-- 文件权限体系：文件夹-角色授权表
CREATE TABLE IF NOT EXISTS biz_folder_acl (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  folder_id   BIGINT       NOT NULL COMMENT '文件夹ID',
  role_id     BIGINT       NOT NULL COMMENT '角色ID',
  perms       VARCHAR(128) NOT NULL COMMENT '权限列表,逗号分隔:view,upload,edit,delete,share,download',
  created_by  BIGINT       NULL,
  created_at  DATETIME     NULL,
  UNIQUE KEY uk_folder_role (folder_id, role_id)
) COMMENT='文件夹-角色授权表';

-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），迁移只执行一次，直接 ALTER
ALTER TABLE biz_file_folder ADD COLUMN downloadable TINYINT NOT NULL DEFAULT 1 COMMENT '下载开关:1=允许下载,0=仅预览';
ALTER TABLE biz_file ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号,覆盖替换+1';
ALTER TABLE biz_file ADD COLUMN replace_by BIGINT NULL COMMENT '最近替换人';
ALTER TABLE biz_file ADD COLUMN replaced_at DATETIME NULL COMMENT '最近替换时间';
