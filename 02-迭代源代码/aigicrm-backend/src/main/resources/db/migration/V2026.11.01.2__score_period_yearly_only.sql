-- ============================================================
-- 学分周期只留「全年」(2026-11-01 用户令: "学分不要上半年下半年, 只显示全年")
-- 1) 学分明细: 与同年全年同格(item+user)的半年行删除保全年值(生产已核当前无重叠,
--    此步是防御), 其余半年行改写为同年全年。
-- 2) 参与名单: 半年名单中全年缺的用户补进全年名单(追加队尾保序), 再清半年名单行。
-- 幂等: 全部锚定 '%半年', 跑一遍后无半年行, 重跑零效果。
-- ============================================================

-- 1a) 防御: 删与全年撞格的半年明细
DELETE h FROM `biz_score_entry` h
  JOIN `biz_score_entry` y
    ON y.`period` = CONCAT(SUBSTRING(h.`period`, 1, 4), '全年')
   AND y.`item_id` = h.`item_id`
   AND y.`user_id` = h.`user_id`
 WHERE h.`period` LIKE '%半年';

-- 1b) 其余半年明细并入同年全年
UPDATE `biz_score_entry`
   SET `period` = CONCAT(SUBSTRING(`period`, 1, 4), '全年')
 WHERE `period` LIKE '%半年';

-- 2a) 半年名单中全年缺的用户补进全年名单(1000+原序号=队尾追加)
INSERT INTO `biz_score_participant` (`period`, `user_id`, `sort_order`, `tenant_id`)
SELECT CONCAT(SUBSTRING(h.`period`, 1, 4), '全年'), h.`user_id`, 1000 + h.`sort_order`, h.`tenant_id`
  FROM `biz_score_participant` h
 WHERE h.`period` LIKE '%半年'
   AND NOT EXISTS (
     SELECT 1 FROM `biz_score_participant` y
      WHERE y.`period` = CONCAT(SUBSTRING(h.`period`, 1, 4), '全年')
        AND y.`user_id` = h.`user_id`
   );

-- 2b) 清掉半年名单行
DELETE FROM `biz_score_participant` WHERE `period` LIKE '%半年';
