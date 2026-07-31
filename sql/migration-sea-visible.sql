-- ============================================================
-- 公海池配置改造: 包含区域/可见角色 -> 可见部门/人员
-- 新增 visible_dept_ids / visible_user_ids 两列
-- region_ids 改为"由可见部门/人员派生的办事处缓存",用于客户释放路由
-- view_role_ids 废弃保留(不再使用)
-- ============================================================

ALTER TABLE biz_sea_config
    ADD COLUMN visible_dept_ids VARCHAR(500) COMMENT '可见部门ID(逗号分隔,dept_type=4)' AFTER view_role_ids;
ALTER TABLE biz_sea_config
    ADD COLUMN visible_user_ids VARCHAR(500) COMMENT '可见人员ID(逗号分隔)' AFTER visible_dept_ids;

-- 回填: 每个现有池的可见部门 = 其所属办事处(region_ids)下的全部 type=4 部门(任意层级)
-- 生产 type=4 部门多级嵌套(办事处->销售部->团队->子团队),需递归向上找最近 type=3 办事处
-- region_ids 保持不变(本就是办事处ID),释放路由不受影响
DROP TEMPORARY TABLE IF EXISTS tmp_dept_office;
CREATE TEMPORARY TABLE tmp_dept_office AS
WITH RECURSIVE chain AS (
    -- 每个 type=4 部门为叶子, node 从自身开始向上爬
    SELECT d.id AS leaf_id, d.id AS node_id, d.parent_id, d.dept_type
    FROM sys_dept d WHERE d.dept_type = 4
    UNION ALL
    SELECT c.leaf_id, p.id, p.parent_id, p.dept_type
    FROM chain c JOIN sys_dept p ON p.id = c.parent_id
    WHERE c.dept_type <> 3   -- 当前节点已是办事处则停止上爬
)
SELECT leaf_id, node_id AS office_id FROM chain WHERE dept_type = 3;

UPDATE biz_sea_config c
SET c.visible_dept_ids = (
    SELECT GROUP_CONCAT(t.leaf_id ORDER BY t.leaf_id)
    FROM tmp_dept_office t
    WHERE FIND_IN_SET(t.office_id, c.region_ids)
)
WHERE c.region_ids IS NOT NULL AND c.region_ids <> '';

DROP TEMPORARY TABLE IF EXISTS tmp_dept_office;

-- 回填可见人员: 极少数用户 dept_id 直接挂在办事处(type=3)而非 type=4 部门(如行政),
-- dept 回填漏掉他们,这里按 office_id 补进 visible_user_ids 以保留其现有可见性。
-- 排除 all 数据范围(管理员,本就全可见,无需显式加入)。
UPDATE biz_sea_config c
SET c.visible_user_ids = (
    SELECT GROUP_CONCAT(u.id ORDER BY u.id)
    FROM sys_user u LEFT JOIN sys_dept d ON d.id = u.dept_id
    WHERE u.deleted = 0 AND u.status = 1
      AND (d.dept_type IS NULL OR d.dept_type <> 4)
      AND FIND_IN_SET(u.office_id, c.region_ids)
      AND u.id NOT IN (
          SELECT ur.user_id FROM sys_user_role ur
          JOIN sys_role r ON r.id = ur.role_id WHERE r.data_scope = 'all'
      )
)
WHERE c.region_ids IS NOT NULL AND c.region_ids <> '';

-- 校验
-- SELECT id, pool_name, region_ids, visible_dept_ids, visible_user_ids FROM biz_sea_config;
