-- ============================================================
-- V2026.11.06.1__normalize_pure_perm_rows_menu_type.sql
-- 【2026-11-06 用户令"权限登记格式分裂, 优化"】纯权限行(有码无路由: permission 非空,
--   path/component 皆空)menu_type 归一为 3(按钮权限点)——测试库(生产快照)实勘现状:
--   type3×58(38 显 + 20 隐) vs type2×1(日志查看, V2026.11.05.3 按当时"激励编辑先例"以
--   type2 形态重登记); 历史上 type2×7 vs type3×45 的分裂已随历次迁移自然收敛, 本迁移
--   收口最后一行并锚死约定: 纯权限行一律 type3, 禁止再以 type2 形态登记(页面节点必须带 path)。
-- 安全面(逐面实证 2026-11-06):
--   ① authorities 不受影响: selectPermissionsByUserId(SysMenuMapper:12-16)联表只过滤
--     permission 非空+status=1, 无 menu_type 条件;
--   ② 侧边栏不受影响: AuthService 菜单树仅收 menu_type=1 ∨ (2 且 path 非空)——纯权限行
--     (path 空)本就不进侧栏, 转 type3 后同样不进;
--   ③ 角色保存闭包只补 menu_type=1 目录锚点(SysRoleService:277), 与本迁移无关;
--   ④ 权限中心矩阵/角色勾选树对 type3 行的展示为多数形态(58 行已在用), 日志查看并入即对齐;
--   ⑤ 本地 fixture seed-local-permission-fixture.sql:47 本就以 type3 建模 system:log:view。
-- 幂等: menu_type<>3 守卫, 重复跑零变化; 缓存: 末尾 perm_version 全员 +1(直改库自愈)。
-- 回滚(人工谨慎, 仅在确有 type2 形态依赖时):
--   UPDATE sys_menu SET menu_type=2 WHERE permission<>''
--     AND COALESCE(path,'')='' AND COALESCE(component,'')='';
--   UPDATE sys_user SET perm_version = perm_version + 1;
-- ============================================================

-- 1) 纯权限行归一为按钮权限点形态(type3)
UPDATE `sys_menu`
SET `menu_type` = 3
WHERE `permission` IS NOT NULL AND `permission` <> ''
  AND COALESCE(`path`, '') = '' AND COALESCE(`component`, '') = ''
  AND `menu_type` <> 3;

-- 2) 权限缓存自愈(直改库须手动失效: 全员 perm_version + 1)
UPDATE sys_user SET perm_version = perm_version + 1;
