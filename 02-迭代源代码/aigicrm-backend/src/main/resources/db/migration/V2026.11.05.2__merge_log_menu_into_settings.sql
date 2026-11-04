-- ============================================================
-- V2026.11.05.2__merge_log_menu_into_settings.sql
-- 【2026-11-05 用户令"日志管理也放到系统设置里面"】
-- 日志管理不再是独立侧栏菜单: 删除菜单节点 /system/log(type=2)及其授权行,
-- 页面转为 系统设置(config.vue) 内「日志管理」页签(前端同批, embedded 形态,
-- 页签显隐=注册码 system:log:view)。页面码不变(system:log:view), 后端接口
-- /system/logs(/audit-logs) 零 Java 改动; 权限规则回收站等其余系统管理菜单不受影响。
-- 机制依据(同 V2026.11.05.1): 节点引用按 path+menu_type 归位, 零硬编码 id;
-- 删除天然幂等; 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚(人工谨慎): 按 V2026.10.21 前种子行形态重插节点(path='/system/log',
-- permission='system:log:view', component='system/log', menu_type=2)并重绑角色,
-- 再 UPDATE sys_user SET perm_version = perm_version + 1。
-- ============================================================

-- 1) 删除授权行(派生表包装规避 MySQL 同表更新限制)
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT m.id FROM (SELECT id FROM sys_menu WHERE path = '/system/log' AND menu_type = 2) m);

-- 2) 删除菜单节点
DELETE FROM sys_menu WHERE path = '/system/log' AND menu_type = 2;

-- 3) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
