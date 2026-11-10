-- 【2026-11-10 用户拍板】文件权限三层定型: 类型(公共/私人/四项激励)→文件夹→文件。
-- ① 授权表 +scope_level: 类型默认按角色配置时"区分数据范围"(全部/本大区/本办事处/本部门/本人;
--    词表与 DataScopeType 同源), NULL=不限; ② 四项激励成为第三类型(仅文件动作, 无文件夹层)。
ALTER TABLE biz_folder_type_default
    ADD COLUMN scope_level VARCHAR(10) NULL COMMENT '数据范围: self|dept|office|region|all (NULL=不限)' AFTER scope_org_id;
