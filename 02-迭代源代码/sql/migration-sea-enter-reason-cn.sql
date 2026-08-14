-- 统一公海池 biz_sea_pool.enter_reason 取值为中文，使其与前端筛选选项及手动释放写入值一致
-- 背景：前端筛选发送英文代码(protection_expired/abandoned/resignation)，
--       但手动释放写入的是中文(保护期满/放弃客户/离职交接)，定时任务写英文 protection_expired，
--       用户离职写 离职，导致「全部原因」筛选匹配不上。统一为中文后筛选生效。
-- 安全：仅 UPDATE 既有不一致取值，幂等可重复执行。

UPDATE biz_sea_pool SET enter_reason = '保护期满'   WHERE enter_reason = 'protection_expired';
UPDATE biz_sea_pool SET enter_reason = '放弃客户'   WHERE enter_reason = 'abandoned';
UPDATE biz_sea_pool SET enter_reason = '离职交接'   WHERE enter_reason = 'resignation';
UPDATE biz_sea_pool SET enter_reason = '离职交接'   WHERE enter_reason = '离职';
