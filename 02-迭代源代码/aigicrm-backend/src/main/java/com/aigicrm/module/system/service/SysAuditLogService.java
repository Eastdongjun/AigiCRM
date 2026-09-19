package com.aigicrm.module.system.service;

import com.aigicrm.module.system.entity.SysAuditLog;
import com.aigicrm.module.system.mapper.SysAuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【BS-R 2026-09-19】安全审计日志只读查询。行转 Map 出参——控制器层不得依赖 entity/mapper
 * (ArchitectureTest 分层约束),SysLogController 的实体直出属存量技术债,不复制。
 */
@Service
@RequiredArgsConstructor
public class SysAuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysAuditLogMapper auditLogMapper;

    public IPage<Map<String, Object>> page(int current, int size, String eventType, Long actorId) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .eq(eventType != null && !eventType.isBlank(), SysAuditLog::getEventType, eventType)
                .eq(actorId != null, SysAuditLog::getActorId, actorId)
                .orderByDesc(SysAuditLog::getId);
        return auditLogMapper.selectPage(new Page<>(current, Math.min(size, MAX_PAGE_SIZE)), wrapper)
                .convert(SysAuditLogService::toRow);
    }

    private static Map<String, Object> toRow(SysAuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("eventType", log.getEventType());
        row.put("actorId", log.getActorId());
        row.put("actorUsername", log.getActorUsername());
        row.put("detail", log.getDetail());
        row.put("ip", log.getIp());
        row.put("createdAt", log.getCreatedAt());
        return row;
    }
}
