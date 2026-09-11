-- ============================================================
-- V23__file_permission_split_nine_items.sql
-- 文件权限模型 v2：六项混用 → 九项分离（文件夹操作与文件操作拆分）
--
-- 背景(2026-08-27 用户拍板):
--   旧 6 项权限存在动作混用: upload 兼"上传文件+建子夹"、edit 兼"替换文件+重命名夹"、
--   delete 兼"删文件+删夹"。拆分为九项:
--   文件夹操作: folder:create / folder:rename / folder:delete
--   文件操作:   file:upload / file:download / file:view / file:edit / file:delete / file:share
--
-- 存量迁移映射(旧→新):
--   upload   → file:upload,folder:create
--   edit     → file:edit,folder:rename
--   delete   → file:delete,folder:delete
--   share    → file:share
--   download → file:download
--   view     → file:view
--
-- 幂等: 逐条 UPDATE 只改一次(perms 已含 file: 前缀则跳过)。
-- 回滚: 见文件尾部回滚注释。
-- ============================================================
-- 1) 迁移存量 ACL perms 字符串(旧 6 项 → 新 9 项)
-- 先查存量分布以便核对(注释保留): 当前仅 1 条: folder 7 公司文件 → 销售员 upload,edit,delete,share,download
UPDATE `biz_folder_acl`
SET `perms` = 'file:upload,folder:create,file:edit,folder:rename,file:delete,folder:delete,file:share,file:download,file:view'
WHERE `perms` LIKE '%upload%'
    AND `perms` NOT LIKE '%file:upload%';
-- 2) 兜底: 任何残留的旧 6 项字符串统一替换(逐项, 防止部分迁移)
--    (仅处理未含新码的行; MySQL 无数组类型, 按逗号分隔字符串替换)
UPDATE `biz_folder_acl`
SET `perms` = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            `perms`,
                            'upload',
                            'file:upload,folder:create'
                        ),
                        'edit',
                        'file:edit,folder:rename'
                    ),
                    'delete',
                    'file:delete,folder:delete'
                ),
                'share',
                'file:share'
            ),
            'download',
            'file:download'
        ),
        'view',
        'file:view'
    )
WHERE `perms` NOT LIKE '%file:%';
-- ==================== 回滚(按需手工执行) ====================
-- 执行前先备份: mysqldump ycmn biz_folder_acl > biz_folder_acl_backup_$(date +%F).sql
-- 回滚 SQL(新 9 项 → 旧 6 项):
--   UPDATE biz_folder_acl SET perms = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(perms,
--     'file:upload,folder:create','upload'),
--     'file:edit,folder:rename','edit'),
--     'file:delete,folder:delete','delete'),
--     'file:share','share'),
--     'file:download','download'),
--     'file:view','view');
-- ============================================================
