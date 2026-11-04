-- ============================================================
-- V2026.11.05.3__reregister_log_view_code.sql
-- 【2026-11-05 纠偏补丁(自纠)】V2026.11.05.2 删除 /system/log 页面节点时连带删掉了
-- system:log:view 码的唯一载体(该码无独立按钮行, 只寄宿在页面节点上)——码从注册表消失:
-- 权限中心矩阵再无可勾行, 页签显隐(前端按 system:log:view)对非 admin 永久失效。
-- 本迁移按 V2026.09.11.6「激励编辑/激励删除」按钮行先例(menu_type=2 + path/component 空
-- + permission 挂码)把 日志查看 码回归注册表, 挂系统管理组下。
-- 授权语义: 测试库(生产快照)实证 0 角色持此码(原先仅 admin 走旁路可见), 无需存量回填;
-- 此后管理员可在 权限中心(系统设置内) 给角色勾「日志查看」→ 该角色即见 系统设置 内日志页签。
-- 幂等: permission NOT EXISTS 守卫; 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚: DELETE FROM sys_menu WHERE permission='system:log:view' AND menu_type=2 AND path='';
--       UPDATE sys_user SET perm_version = perm_version + 1;
-- ============================================================

-- 1) 日志查看码回归注册表(挂系统管理组, 按名归位零硬编码 id; NOT EXISTS 防重复)
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE((SELECT p.id FROM `sys_menu` p
                  WHERE p.menu_name = '系统管理' AND p.menu_type = 1
                  ORDER BY p.id LIMIT 1), 0),
       '日志查看', 2, '', '', 'system:log:view', '', 96, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.permission = 'system:log:view');

-- 2) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
