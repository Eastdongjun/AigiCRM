-- 激励上传记录：新增「安装前问题 / 安装前·中·后三个文件 / 备注」字段
-- 积分口径：一条记录的 score = 子类分值 × 该记录的文件数（安装前/中/后中非空者计数）
-- 生产 biz_incentive_record 已含 customer_id、shared，此处仅补 5 个新列。
-- MySQL 8 无 ADD COLUMN IF NOT EXISTS，故用存储过程做幂等判断。

DROP PROCEDURE IF EXISTS add_incentive_install_cols;
DELIMITER $$
CREATE PROCEDURE add_incentive_install_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'pre_install_question') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN pre_install_question TEXT COMMENT '安装前问题' AFTER file_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'file_pre_id') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN file_pre_id VARCHAR(500) COMMENT '安装前文件ID列表' AFTER pre_install_question;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'file_mid_id') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN file_mid_id VARCHAR(500) COMMENT '安装中文件ID列表' AFTER file_pre_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'file_post_id') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN file_post_id VARCHAR(500) COMMENT '安装后文件ID列表' AFTER file_mid_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'remark') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN remark VARCHAR(500) COMMENT '备注' AFTER file_post_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_incentive_record' AND COLUMN_NAME = 'file_track_id') THEN
        ALTER TABLE biz_incentive_record ADD COLUMN file_track_id VARCHAR(500) COMMENT '跟踪文件ID列表' AFTER file_post_id;
    END IF;
END$$
DELIMITER ;

CALL add_incentive_install_cols();
DROP PROCEDURE IF EXISTS add_incentive_install_cols;
