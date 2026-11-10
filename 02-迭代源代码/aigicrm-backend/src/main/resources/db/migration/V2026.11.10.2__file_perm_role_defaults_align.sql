-- 【2026-11-10 文件权限终版·二段：业界口径回归】用户拍板"1 2 3 4"。
-- ① 治空城: 查看档默认=全部(公司文件全员可看可下载, 钉钉盘/Seafile/Google 同口径;
--    原 self 默认致非 admin 打开文件管理近乎空城——实测 NO001 体验坐实);
--    管理档默认=本部门(dept, 钉钉"部门协作文件夹"同款: 本部门可传/改/换);
--    删除档维持 self(拍板③高敏谨慎)。
-- ②/④ 的代码面(文件跟夹走/负责人建夹)不涉本迁移; 档位放开后管理员仍可在
--    权限中心·功能与范围 逐角色调档(即时生效)。
UPDATE role_perm_scope SET scope_level = 'all'  WHERE module = 'file' AND action = 'view';
UPDATE role_perm_scope SET scope_level = 'dept' WHERE module = 'file' AND action = 'manage';
UPDATE role_perm_scope SET scope_level = 'self' WHERE module = 'file' AND action = 'delete';
