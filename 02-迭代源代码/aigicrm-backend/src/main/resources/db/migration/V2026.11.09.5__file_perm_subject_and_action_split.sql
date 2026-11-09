-- 【2026-11-09 文件权限 v2：主体扩展 + 动作细分】设计拍板(用户令"按照建议动工")：
-- ① 授权主体从"仅角色"扩展为 ROLE|USER——管理员可勾选"某个人×某空间×某动作"，保存即实时生效；
-- ② 类型层词表从 edit 伞拆出 rename/replace 两个独立动作(存量 edit 由判定层读时展开, 数据不动)；
-- ③ 类型默认从"建夹时物化进 ACL"改为"判定链实时生效"——勾了作用于该类型全部存量+未来文件夹，
--    FileService.createFolder 不再调用 materializeAcl(新正向语义, 取消勾选即收回)。
-- 空 DDL 兼容: 生产 biz_folder_type_default 为空表; 测试服演示行(行政×public)随 subject_type 默认成为 ROLE 行。
ALTER TABLE biz_folder_type_default
    ADD COLUMN subject_type VARCHAR(10) NOT NULL DEFAULT 'ROLE' COMMENT '授权主体: ROLE=角色 | USER=人员' AFTER folder_type,
    CHANGE COLUMN role_id subject_id BIGINT NOT NULL COMMENT '主体 id: sys_role.id | sys_user.id',
    DROP KEY uk_type_role,
    ADD UNIQUE KEY uk_type_subject (folder_type, subject_type, subject_id);
