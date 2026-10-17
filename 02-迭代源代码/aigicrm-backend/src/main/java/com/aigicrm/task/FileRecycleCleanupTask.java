package com.aigicrm.task;

import com.aigicrm.module.system.service.RecycleService;
import com.aigicrm.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 【文件回收站 2026-10-18 用户裁决】删除的文件先进入回收站, 超过保留期后清理。
 *
 * <p>删除改为只做逻辑删(FileService.deleteFile 不再物理清除), 本任务每天按
 * sys_config `file.recycle.retention.days`(默认 30, 0=不自动清理) 清理过期文件:
 * 先删存储内容, 再删台账行。清理走 JdbcTemplate 直连 SQL, 不受会话租户拦截器影响,
 * 覆盖全部集团(系统级维护语义)。</p>
 *
 * <p>管理员也可在「回收站 → 已删除文件」手动清空(立即清理全部), 见 RecycleService.purgeAllDeletedFiles。</p>
 */
@Component
@RequiredArgsConstructor
public class FileRecycleCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(FileRecycleCleanupTask.class);
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final RecycleService recycleService;
    private final SysConfigService configService;

    /** 每天 03:30 清理, 与客户到期任务(02:00)错峰 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeExpiredFiles() {
        int retentionDays = resolveRetentionDays();
        if (retentionDays <= 0) {
            log.info("文件回收站自动清理未启用(file.recycle.retention.days={})", retentionDays);
            return;
        }
        try {
            int purged = recycleService.purgeExpiredFiles(retentionDays);
            log.info("文件回收站自动清理完成: 保留期={}天, 清理={}个文件", retentionDays, purged);
        } catch (Exception e) {
            // 定时任务失败不影响主流程, 留痕待人工核查(下次运行会重试同一批)
            log.error("文件回收站自动清理失败: {}", e.getMessage(), e);
        }
    }

    private int resolveRetentionDays() {
        String value = configService.getValue("file.recycle.retention.days");
        if (value == null || value.isBlank()) return DEFAULT_RETENTION_DAYS;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("file.recycle.retention.days 配置脏值({}), 回退默认{}天", value, DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
