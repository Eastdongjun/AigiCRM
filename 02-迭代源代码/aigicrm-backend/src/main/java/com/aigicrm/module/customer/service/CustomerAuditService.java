package com.aigicrm.module.customer.service;

import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 客户/财务敏感操作审计落库（sys_log 快照）。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出；尽力而为不阻断主流程，
 * 但落库失败必须可见，不能空吞。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerAuditService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAuditService.class);

    private final SysLogMapper financeAuditLogMapper;

    /** 记录删除审计:完整快照落 sys_log；失败只告警不阻断。 */
    public void logRecordDeletion(String module, String action, String targetType, Long recordId, String detail) {
        try {
            SysLog audit = new SysLog();
            SecurityUser current = SecurityUtils.getCurrentUser();
            audit.setUserId(current != null ? current.getUserId() : null);
            audit.setUsername(current != null ? current.getUsername() : null);
            audit.setModule(module);
            audit.setAction(action);
            audit.setTargetType(targetType);
            audit.setTargetId(recordId);
            audit.setDetail(detail);
            financeAuditLogMapper.insert(audit);
        } catch (Exception e) {
            log.warn("审计落库失败 module={} action={} recordId={}", module, action, recordId, e);
        }
    }

    public void logFinanceDeletion(String kind, Long customerId, Long recordId, String reason, String snapshot) {
        logRecordDeletion("财务", "删除" + kind, "finance_record", recordId,
                snapshot + "; 原因: " + reason + "; customerId=" + customerId);
    }
}
