-- V2026.11.23.2 法布施复诵（D14，Q6 拍板=独立实体表）
-- 语义：他人对已发布内容（三类型皆可）的朗读/背诵；同人同帖可多次（日积月累），计数=行数。
-- 权限：发布复诵=fabushi:create（audio 必挂 scope=fabushi 文件，≤60s）；查看=fabushi:view；
--       复诵音频社区放行（published 帖下的复诵音频）= isFabushiCommunityFile 第四类（本批同目录 FolderPermissionService）。
CREATE TABLE IF NOT EXISTS biz_fabushi_recitation (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` BIGINT NOT NULL COMMENT '被复诵的内容(biz_fabushi_post)',
  `user_id` BIGINT NOT NULL COMMENT '复诵人(sys_user)',
  `audio_file_id` BIGINT NOT NULL COMMENT '复诵音频(biz_file, file_scope=fabushi)',
  `duration_seconds` INT DEFAULT NULL COMMENT '复诵时长(秒, ≤60; 未知暂放行沿 F1 口径)',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_fb_recite_post_created` (`post_id`, `created_at`),
  KEY `idx_fb_recite_user` (`user_id`),
  KEY `idx_fb_recite_audio` (`audio_file_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '法布施复诵记录(D14 2026-11-23): 他人朗读/背诵他人内容, 不计入本人每日诵读完成度';
