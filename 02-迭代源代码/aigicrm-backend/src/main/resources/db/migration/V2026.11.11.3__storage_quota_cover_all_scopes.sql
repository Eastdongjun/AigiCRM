-- 【T2 2026-11-11 审计整改】存储配额口径覆盖全部文件范围——存量回溯对账
--
-- 背景(审计 P1-2): 历史上仅 private scope 扣配额, public/incentive 文件(激励佐证为主体,
-- 生产 2.5k+ 条)从未计入 storage_used。FileService 改为全 scope 扣/还后, 若不回溯存量,
-- 删除存量激励文件会归还"从未扣过的配额"(凭空赠送, 账面永久负漂移)。
--
-- 生产只读预查(2026-11-11): 全员 storage_quota=10GB; 回溯最大 943MB(TY005), 回溯后
-- 峰值占用 <10%, 无人超配, 零业务冲击。
--
-- 幂等性: 空库重放时 biz_file 为空 → SUM 为 NULL → COALESCE 0 → 无变化;
-- 存量库迁移只执行一次(flyway 契约)。锚定纪律: 按业务键聚合, 零行 id 定位(INV-MIG-001/002)。
UPDATE `sys_user` u
SET u.`storage_used` = u.`storage_used` + COALESCE((
    SELECT SUM(f.`file_size`)
    FROM `biz_file` f
    WHERE f.`uploader_id` = u.`id`
      AND f.`file_scope` IN ('public', 'incentive')
      AND f.`deleted` = 0
), 0);
