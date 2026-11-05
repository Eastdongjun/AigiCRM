-- ============================================================
-- V2026.11.05.9__remove_file_edit_dead_code.sql
-- 【2026-11-05 用户令"替换文件这个功能不要了 删除了"】file:edit 权限点全链退役:
--   其唯一消费者 /file/{id}/replace 端点已删(FileController 端点/FileService 方法/
--   PC FileTable 行内入口/三处测试用例一并退役, 契约正向钉已翻防复活守卫)。
--   本迁移清权限面: PC/MP/后端对 file:edit 零引用(grep 实据), 死码按 V22
--   cleanup_dead_menu_permissions_by_code 先例按 permission 锚点清除。
--   不删 file:view/upload/delete/share/folder:manage——四码各有存活消费者。
-- 幂等: DELETE 天然幂等; 末尾 perm_version 全员 +1(直改库缓存自愈)。
-- 回滚(人工谨慎): 按 V26/V2026.09.11.6 形态重建 type3 '文件编辑' 行并重绑角色。
-- ============================================================

-- 1) 清授权行(先删绑定再删节点, 与菜单组删除同纪律)
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT m.id FROM (SELECT id FROM sys_menu WHERE permission = 'file:edit') m);

-- 2) 删权限节点(历史上 V26 与 V2026.09.11.6 各注册过一行, 按 permission 锚点全清)
DELETE FROM sys_menu WHERE permission = 'file:edit';

-- 3) 权限缓存自愈(全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
