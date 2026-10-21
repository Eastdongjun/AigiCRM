package com.aigicrm.module.system.service;

import com.aigicrm.module.system.entity.SysAuditLog;
import com.aigicrm.module.system.mapper.SysAuditLogMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;

    public IPage<Map<String, Object>> page(int current, int size, String eventType, Long actorId) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .eq(eventType != null && !eventType.isBlank(), SysAuditLog::getEventType, eventType)
                .eq(actorId != null, SysAuditLog::getActorId, actorId)
                .orderByDesc(SysAuditLog::getId);
        // 【A2 2026-10-20 审计·集团边界】sys_audit_log 无 tenant_id/group_id 列、不进租户拦截器,
        // 旧实现无任何范围条件——持 system:log:view 的非 admin 角色(迁移曾把该码绑给 role '0' 销售总监)
        // 能读到全平台所有集团的登录失败/越权拒绝/权限变更事件(含用户名/IP/URI)。
        // 与 SysLogService.buildBase 同口径收口: admin 全量; 非 admin 按当前集团成员 actorId 集合过滤;
        // 空集(无集团归属/集团无成员)fail-closed 返回空页。actor_id 为 NULL 的匿名事件(登录失败等)
        // 不落在任何成员集合内,IN 语义天然不匹配 → 非 admin 不可见,避免跨集团 IP 泄露。
        if (!SecurityUtils.isAdmin()) {
            List<Long> uids = scopeResolver.resolveModuleUsersWithinGroup(
                    SecurityUtils.getCurrentUser(), "system");
            if (uids == null) {
                // 仅 admin 会到达此处(WithinGroup 已保证非 admin 不返回 null),防御性放行
            } else if (uids.isEmpty()) {
                wrapper.eq(SysAuditLog::getActorId, Long.MIN_VALUE); // 空集 → 空页
            } else {
                wrapper.in(SysAuditLog::getActorId, uids);
            }
        }
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
