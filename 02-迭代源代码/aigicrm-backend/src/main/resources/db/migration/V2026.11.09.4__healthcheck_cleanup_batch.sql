-- 【2026-11-09 体检黄牌清理批】七项一次性处置(全部经 11-09 权限核查/七册体检取证, 用户令"处理掉")。
-- 行定位全部走自然键(username/role_code/permission 码/悬空 JOIN), 遵守 INV-MIG-001 禁硬编码 id。
-- P1 激励旧授权死表 DROP(29 行已留档 out/归档-biz_incentive_permission死表29行-20261109.txt;
--    全后端零引用实证, 活口径=biz_incentive_grant)
-- P3 规则引擎 5 条启用规则停用(绑定表 sys_role_rule/sys_position_rule 均空, 规则永不加载——
--    停用使库与现实一致, 防误解"配了会生效"; 能力保留, 未来补绑定入口后再启用)
-- P5 测试号清理: TY101(真名"测试")/smoke_sales 软删 + 角色绑定/范围行/其上传文件软删
-- P6 角色默认档双轨归一: 06 列 data_scope team→self 与范围表通配行一致(实拉全系统零多角色用户,
--    06 有 *:*=self 表行覆盖解析, 列仅并集/回退路径使用 → 零行为变化)
-- P7 system:log:view 绑定 admin+销售总监(与 system:menu:view 同款), 菜单按 permission 码定位
-- P9 perm_scope_org 悬空行清(scope_id 指向已不存在范围配置的行, LEFT JOIN 语义定位)
-- P2附 石力(TY008)/王建平(NM006) 零下属冗余 team 覆盖行删(零行为变化: team 无下属==self;
--    陈晓彬/冀晓伟/白晓炯 有直接下属=真主管, 行保留)

DROP TABLE IF EXISTS biz_incentive_permission;

UPDATE sys_permission_rule SET status = 0 WHERE status = 1;

-- P5: 按 username 定位测试号
UPDATE sys_user SET deleted = 1
WHERE username IN ('TY101', 'smoke_sales') AND deleted = 0;
DELETE FROM sys_user_role WHERE user_id IN
    (SELECT id FROM sys_user WHERE username IN ('TY101', 'smoke_sales'));
DELETE FROM role_perm_scope WHERE assignee_type = 'USER' AND assignee_key IN
    (SELECT CAST(id AS CHAR) FROM sys_user WHERE username IN ('TY101', 'smoke_sales'));
UPDATE biz_file SET deleted = 1 WHERE uploader_id IN
    (SELECT id FROM sys_user WHERE username IN ('TY101', 'smoke_sales')) AND deleted = 0;

-- P6: 06 角色默认档与范围表一致(零多角色用户实证, 零行为变化)
UPDATE sys_role SET data_scope = 'self' WHERE role_code = '06' AND data_scope = 'team';

-- P7: log:view 按码绑角色(admin+销售总监, 同 system:menu:view 口径)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.permission = 'system:log:view'
WHERE r.role_code IN ('admin', '0')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- P9: 悬空 org 落点(语义定位: 指向不存在范围配置的行)
DELETE po FROM perm_scope_org po
LEFT JOIN role_perm_scope rps ON rps.id = po.scope_id
WHERE rps.id IS NULL;

-- P2附: 零下属冗余 team 覆盖行(按 username 定位)
DELETE FROM role_perm_scope
WHERE assignee_type = 'USER' AND module = '*' AND action = '*' AND scope_level = 'team'
  AND assignee_key IN (SELECT CAST(id AS CHAR) FROM sys_user WHERE username IN ('TY008', 'NM006'));
