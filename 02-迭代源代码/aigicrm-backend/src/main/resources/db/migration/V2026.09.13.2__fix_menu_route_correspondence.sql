-- 2026-09-13 菜单与前端页面对应关系修复
--
-- 来源：系统设置/权限管理 四方核查（菜单↔路由↔组件↔权限码），处置项 #1 #2 #3。
--
-- #1「客户排行」菜单缺失（重点）：
--    /ranking/customer（客户排行页，含"核心客户六项指标排行" Tab）在库中没有菜单行，
--    只能手输 URL 打开。**该菜单此前已有三个迁移负责创建**（V30 score_menu_and_customer_ranking_menu、
--    V31 fix_customer_ranking_menu_parent、V32 fix_customer_ranking_menu_parent_from_statistics_route），
--    三者在测试库 flyway_schema_history 中 success=1（2026-09-11 应用），但行至今不存在 ——
--    原因是三处都是 `INSERT INTO sys_menu ... SELECT ... FROM sys_menu`（依赖既有行作父级来源），
--    当时的菜单结构下子查询匹配为空 ⇒ **静默插入 0 行**（与契约脚本记录的 V25 事故同类）。
--    本迁移按当前结构重新创建，并把父级查找做成"统计页的父级 → 类型1分组"两级回退 + 非空守卫，
--    避免再次落 NULL 父级；同时用 `FROM DUAL` 常量源 + NOT EXISTS 保持幂等。
--    说明：type=3 的按钮码 ranking:customer:view 保留不动（它是 CustomerController 与小程序
--    「排行」页的备选授权码，不是死码）。
--
-- #2 菜单 component 字段 3 处指向不存在的文件（system/position/index、system/group/index、
--    system/rule/index 在 views/ 下都没有），组织架构组节点的 component 也不对应任何页面
--    → 按实际文件修正。前端不消费该字段（路由为静态定义），此处仅消除误导。
--
-- #3「积分排行」菜单码 score:ranking 后端 0 处校验（页面接口用 score:view，5 处引用）
--    → 菜单码统一为 score:view，与前端路由 meta.permission 一致。
--
-- 幂等：全部带 WHERE 条件或 NOT EXISTS 守卫，可重复执行。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE path='/ranking/customer');
--   DELETE FROM sys_menu WHERE path='/ranking/customer';
--   UPDATE sys_menu SET component='system/position/index' WHERE path='/system/position';
--   UPDATE sys_menu SET component='system/group/index'    WHERE path='/system/group';
--   UPDATE sys_menu SET component='system/rule/index'     WHERE path='/system/rule';
--   UPDATE sys_menu SET component='system/org'            WHERE path='/system/org-group';
--   UPDATE sys_menu SET permission='score:ranking'        WHERE path='/score/ranking';

-- #1 补「客户排行」菜单（父级 = /statistics 页所在的一级分组，回退到名为「考核与分析」的一级分组）
INSERT INTO `sys_menu` (`parent_id`, `menu_name`, `menu_type`, `path`, `component`, `permission`,
                        `icon`, `sort_order`, `visible`, `status`)
SELECT COALESCE(
           (SELECT p.`id` FROM `sys_menu` s JOIN `sys_menu` p ON p.`id` = s.`parent_id`
             WHERE s.`path` = '/statistics' AND p.`menu_type` = 1 ORDER BY p.`id` LIMIT 1),
           (SELECT g.`id` FROM `sys_menu` g
             WHERE g.`menu_type` = 1 AND g.`menu_name` = '考核与分析' ORDER BY g.`id` LIMIT 1)
       ),
       '客户排行', 2, '/ranking/customer', 'ranking/customer', 'statistics:view',
       'DataLine', 2, 1, 1
FROM DUAL
WHERE (SELECT COUNT(*) FROM `sys_menu` WHERE `path` = '/ranking/customer') = 0
  AND COALESCE(
          (SELECT p.`id` FROM `sys_menu` s JOIN `sys_menu` p ON p.`id` = s.`parent_id`
            WHERE s.`path` = '/statistics' AND p.`menu_type` = 1 ORDER BY p.`id` LIMIT 1),
          (SELECT g.`id` FROM `sys_menu` g
            WHERE g.`menu_type` = 1 AND g.`menu_name` = '考核与分析' ORDER BY g.`id` LIMIT 1)
      ) IS NOT NULL;

-- 绑定给已持有 statistics:view 的角色（与路由守卫、接口校验同码，保证"看得见就能进"）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.`role_id`, (SELECT `id` FROM `sys_menu` WHERE `path` = '/ranking/customer' LIMIT 1)
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE m.`permission` = 'statistics:view'
  AND (SELECT COUNT(*) FROM `sys_menu` WHERE `path` = '/ranking/customer') = 1
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x
      WHERE x.`role_id` = rm.`role_id`
        AND x.`menu_id` = (SELECT `id` FROM `sys_menu` WHERE `path` = '/ranking/customer' LIMIT 1)
  );

-- #2 component 字段与实际视图文件对齐
UPDATE `sys_menu` SET `component` = 'system/position'
 WHERE `path` = '/system/position' AND `component` = 'system/position/index';

UPDATE `sys_menu` SET `component` = 'system/group'
 WHERE `path` = '/system/group' AND `component` = 'system/group/index';

UPDATE `sys_menu` SET `component` = 'system/rule'
 WHERE `path` = '/system/rule' AND `component` = 'system/rule/index';

-- 组织架构 (/system/org-group) 是侧边栏组节点，本身没有页面
UPDATE `sys_menu` SET `component` = NULL
 WHERE `path` = '/system/org-group' AND `component` = 'system/org';

-- #3 积分排行菜单码与路由/后端统一
UPDATE `sys_menu` SET `permission` = 'score:view'
 WHERE `path` = '/score/ranking' AND `permission` = 'score:ranking';
