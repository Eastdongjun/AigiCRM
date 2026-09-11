USE aigicrm;
-- 激励安装文件多文件支持：bigint → varchar(500)（与测试库/实体 String 一致）
ALTER TABLE `biz_incentive_record`
  MODIFY `file_id` varchar(500) DEFAULT NULL,
  MODIFY `file_pre_id` varchar(500) DEFAULT NULL,
  MODIFY `file_mid_id` varchar(500) DEFAULT NULL,
  MODIFY `file_post_id` varchar(500) DEFAULT NULL,
  MODIFY `file_track_id` varchar(500) DEFAULT NULL;
