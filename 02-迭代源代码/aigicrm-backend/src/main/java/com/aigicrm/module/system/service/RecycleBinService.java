package com.aigicrm.module.system.service;

import com.aigicrm.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 【2026-11-17 阶段三】统一回收站台账 biz_recycle_bin 写入器。
 * 主表仍保留软删字段（现有回收站页面继续读主表）；本表只存跨模块统一快照与状态，
 * 便于后续按模块导出/清理/审计。写入必须 best-effort，不得让台账失败阻断业务删除。
 */
// native-sql-audit: 统一回收站台账仅由 admin 回收站端点/删除内核调用; biz_* 行按业务主键定位, 单集团 tenant_id 恒 1
@Service
@RequiredArgsConstructor
public class RecycleBinService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public void record(String bizType, Long bizId, Map<String, Object> snapshot, String reason, String source) {
        if (bizType == null || bizId == null) return;
        try {
            jdbc.update("INSERT INTO biz_recycle_bin " +
                            "(biz_type,biz_id,snapshot_json,deleted_by,deleted_at,reason,source,status) " +
                            "VALUES(?,?,?,?,NOW(),?,?,'deleted') " +
                            "ON DUPLICATE KEY UPDATE snapshot_json=VALUES(snapshot_json), deleted_by=VALUES(deleted_by), " +
                            "deleted_at=VALUES(deleted_at), reason=VALUES(reason), source=VALUES(source), status='deleted', " +
                            "restored_at=NULL, purged_at=NULL",
                    bizType, bizId, JSON.writeValueAsString(snapshot == null ? Map.of() : snapshot),
                    SecurityUtils.getCurrentUserId(), reason, source);
        } catch (Exception ignored) {
            // 台账是旁路审计，不阻断主表删除；失败由主表软删字段与 sys_log 兜底。
        }
    }

    public void markRestored(String bizType, Long bizId) {
        try {
            jdbc.update("UPDATE biz_recycle_bin SET status='restored', restored_at=NOW() " +
                    "WHERE biz_type=? AND biz_id=? AND status='deleted'", bizType, bizId);
        } catch (Exception ignored) {
        }
    }

    public void markPurged(String bizType, Long bizId) {
        try {
            jdbc.update("UPDATE biz_recycle_bin SET status='purged', purged_at=NOW() " +
                    "WHERE biz_type=? AND biz_id=?", bizType, bizId);
        } catch (Exception ignored) {
        }
    }
}
