-- 【2026-11-10 文件权限终版：组织底座】用户令"开干全部都要做"。四拍板+两专业默认(逐夹ACL退役/授权过期):
-- ①B 看也按组织收窄(公共兜底删除) ②激励按组织 ③可删独立+审计 ④默认本人 ⑤私人已清(运维批)。
-- 模型: 文件归属=上传人组织快照 / 文件夹归属=创建人; 可见=resolveModuleUsers(file,view) 组织集+分享+授权;
--       管理=本人恒管∨矩阵(file,manage)∨授权; 删除独立(file,delete)∨授权; 文件夹创建=授权(folder.create)。
--
-- 一、归属快照列: biz_file/biz_file_folder +org_id(上传/建夹时组织节点, 转岗不漂移)
ALTER TABLE biz_file ADD COLUMN org_id BIGINT NULL COMMENT '归属组织快照(上传人当时组织节点)', ADD KEY idx_org (org_id);
ALTER TABLE biz_file_folder ADD COLUMN org_id BIGINT NULL COMMENT '归属组织快照(创建人当时组织节点; 存量公共夹归总部)', ADD KEY idx_org (org_id);
-- 回填: 文件按上传人现组织(办事处优先部门兜底; 无上传人留空=仅admin/授权可达); 夹按 owner, 公共夹(owner NULL)归集团根
UPDATE biz_file f JOIN sys_user u ON u.id = f.uploader_id SET f.org_id = COALESCE(u.office_id, u.dept_id) WHERE f.org_id IS NULL;
UPDATE biz_file_folder fd SET fd.org_id = (SELECT id FROM sys_dept WHERE dept_type = 1 ORDER BY id LIMIT 1)
WHERE fd.org_id IS NULL AND fd.owner_id IS NULL;
UPDATE biz_file_folder fd JOIN sys_user u ON u.id = fd.owner_id SET fd.org_id = COALESCE(u.office_id, u.dept_id) WHERE fd.org_id IS NULL;

-- 二、权限矩阵: file 域三动作行(view 可见/manage 管改/delete 删除③独立), 全角色默认 self ④,
--       放开(部门/办事处/大区/全部)去 权限中心·功能与范围 配——与客户/激励同一引擎同一心智。
--       自然键回填(INV-MIG-001: 禁硬编码 id); 已存在行不动; admin 角色运行时全通无需行。
INSERT INTO role_perm_scope (role_code, module, action, scope_level, assignee_type, assignee_key, anchor)
SELECT r.role_code, 'file', a.action, 'self', 'ROLE', r.role_code, 'OWNER'
FROM sys_role r
CROSS JOIN (SELECT 'view' AS action UNION ALL SELECT 'manage' UNION ALL SELECT 'delete') a
WHERE r.status = 1 AND r.role_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM role_perm_scope x WHERE x.role_code = r.role_code AND x.module = 'file' AND x.action = a.action);

-- 三、例外授权表升级: biz_folder_type_default +object_type(FOLDER/FILE) +expires_at(必填,默认1年) +folder_id(逐夹定向,兼容ACL转换) +scope_org_id(授权组织范围,NULL=不限)
--     撤旧唯一键 uk_type_subject(folder_type,subject_type,subject_id): 一主体一空间只能一行,
--     容不下 FILE+FOLDER 两行(测试库首跑实证=公共 FOLDER 行撞键 1062); 新口径无 DB 唯一键
--     (folder_id NULL 在唯一键下可重复, 语义不成立), 防重由 saveGrant select-before-write + 迁移 dedup 守卫。
ALTER TABLE biz_folder_type_default
    DROP KEY uk_type_subject,
    ADD COLUMN object_type VARCHAR(10) NOT NULL DEFAULT 'FILE' COMMENT '动作对象: FOLDER|FILE' AFTER folder_type,
    ADD COLUMN expires_at DATETIME NOT NULL DEFAULT (DATE_ADD(NOW(), INTERVAL 1 YEAR)) COMMENT '过期时间(到期自动收回)' AFTER perms,
    ADD COLUMN folder_id BIGINT NULL COMMENT '逐夹定向(NULL=该空间全部)' AFTER object_type,
    ADD COLUMN scope_org_id BIGINT NULL COMMENT '授权组织范围(NULL=不限组织)' AFTER folder_id;

-- 四、存量转换:
--   ①公共空间 28 行(用户授权 view,upload,share,download,create) → FILE 行(四动作)+FOLDER 行(create), 1年过期
INSERT INTO biz_folder_type_default (folder_type, object_type, folder_id, scope_org_id, subject_type, subject_id, perms, expires_at, created_by, created_at)
SELECT d.folder_type, 'FILE', NULL, NULL, d.subject_type, d.subject_id, 'view,upload,share,download', DATE_ADD(NOW(), INTERVAL 1 YEAR), d.created_by, NOW()
FROM biz_folder_type_default d WHERE d.folder_type = 'public';
INSERT INTO biz_folder_type_default (folder_type, object_type, folder_id, scope_org_id, subject_type, subject_id, perms, expires_at, created_by, created_at)
SELECT d.folder_type, 'FOLDER', NULL, NULL, d.subject_type, d.subject_id, 'create', DATE_ADD(NOW(), INTERVAL 1 YEAR), d.created_by, NOW()
FROM biz_folder_type_default d WHERE d.folder_type = 'public';
DELETE FROM biz_folder_type_default WHERE object_type = 'FILE' AND FIND_IN_SET('create', perms);
--   ↑ 清掉转换前的旧格式行(旧 FILE 行把 folder.create 混排进 perms): 不变量=FILE 行永不包含 create
--   ②逐夹 ACL 7 行 → 例外授权(object_type=FILE, folder_id 定向, edit→rename+replace 读时展开兼容), 表退役冻结(代码停读, 留档防回滚)
INSERT INTO biz_folder_type_default (folder_type, object_type, folder_id, scope_org_id, subject_type, subject_id, perms, expires_at, created_by, created_at)
SELECT COALESCE(f.folder_type, 'public'), 'FILE', a.folder_id, NULL, 'ROLE', a.role_id,
       TRIM(BOTH ',' FROM REPLACE(CONCAT_WS(',', a.perms, '' ), 'edit', 'rename,replace')),
       DATE_ADD(NOW(), INTERVAL 1 YEAR), a.created_by, NOW()
FROM biz_folder_acl a LEFT JOIN biz_file_folder f ON f.id = a.folder_id;
--   去重守卫: 同(subject,folder_id,object_type)已存在则跳过(重放安全)
DELETE t1 FROM biz_folder_type_default t1
INNER JOIN biz_folder_type_default t2
  ON t1.subject_type = t2.subject_type AND t1.subject_id = t2.subject_id AND t1.folder_id <=> t2.folder_id
 AND t1.object_type = t2.object_type AND t1.id > t2.id;
