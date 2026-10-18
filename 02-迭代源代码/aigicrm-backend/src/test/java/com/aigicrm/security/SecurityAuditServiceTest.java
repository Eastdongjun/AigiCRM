package com.aigicrm.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 【BS-A09 2026-09-19】结构化安全审计:事件格式/阈值告警/降级不阻断。 */
class SecurityAuditServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final com.aigicrm.module.system.mapper.SysAuditLogMapper auditLogMapper =
            mock(com.aigicrm.module.system.mapper.SysAuditLogMapper.class);
    private SecurityAuditService svc;
    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(redis.opsForValue()).thenReturn(ops);
        svc = new SecurityAuditService(redis, auditLogMapper);
        auditLogger = (Logger) org.slf4j.LoggerFactory.getLogger("SECURITY_AUDIT");
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        auditLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void loginFailed_emitsStructuredEventWithNeutralizableFieldsInside() {
        svc.loginFailed("zhangsan", "10.0.0.9", true, true, 2, 3);

        assertTrue(messages().stream().anyMatch(m ->
                        m.startsWith("event=LOGIN_FAILED")
                                && m.contains("username=\"zhangsan\"") && m.contains("ip=\"10.0.0.9\"")
                                && m.contains("userExists=\"true\"") && m.contains("failCount=\"2\"")),
                "结构化事件须含 event/username/ip/存在性/计数,实际: " + messages());
    }

    @Test
    void loginSuccess_emitsStructuredEvent() {
        svc.loginSuccess(7L, "zhangsan", "10.0.0.9");
        assertTrue(messages().stream().anyMatch(m ->
                m.startsWith("event=LOGIN_SUCCESS") && m.contains("userId=\"7\"")));
    }

    @Test
    void anonymousProbe_firesAlertExactlyAtThreshold() {
        when(ops.increment(anyString())).thenReturn(9L, 10L, 11L);

        svc.anonymousProbe("pwd_lookup", "u1", "10.0.0.9");   // 9 → 未到
        svc.anonymousProbe("pwd_lookup", "u2", "10.0.0.9");   // 10 → 恰好告警
        svc.anonymousProbe("pwd_lookup", "u3", "10.0.0.9");   // 11 → 不重复

        long alerts = messages().stream()
                .filter(m -> m.contains("event=SECURITY_ALERT") && m.contains("alert=ANON_PROBE_IP_BURST"))
                .count();
        assertTrue(alerts == 1, "阈值告警恰好一次,实际 " + alerts + ": " + messages());
        // 每次探测本身都有留痕(枚举检测的数据源)
        assertTrue(messages().stream().filter(m -> m.startsWith("event=ANON_PROBE")).count() == 3);
    }

    @Test
    void thresholdCounter_setsWindowOnFirstIncrement() {
        when(ops.increment(anyString())).thenReturn(1L);
        svc.anonymousProbe("pwd_lookup", "u1", "10.0.0.9");
        org.mockito.Mockito.verify(redis).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    void redisDown_degradesWithoutThrowingOrBlocking() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> svc.anonymousProbe("pwd_lookup", "u1", "10.0.0.9"));
        // 事件本身仍要落(枚举痕迹不因 Redis 抖动丢失——阈值告警降级,事件不降级)
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("event=ANON_PROBE")),
                "Redis 故障时事件日志仍须保留");
    }

    @Test
    void accessDenied_and_permissionChange_structured() {
        when(ops.increment(anyString())).thenReturn(1L);
        svc.accessDenied("/api/customer/list", 7L, "10.0.0.9");
        svc.permissionChange("role_update", 3L, "行政", 1L);
        svc.groupSwitch(7L, "zhangsan", 1L, 2L);

        assertTrue(messages().stream().anyMatch(m -> m.startsWith("event=ACCESS_DENIED")
                && m.contains("uri=\"/api/customer/list\"")));
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("event=PERMISSION_CHANGE")
                && m.contains("action=\"role_update\"")));
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("event=GROUP_SWITCH")
                && m.contains("from=\"1\"") && m.contains("to=\"2\"")));
    }

    @Test
    void events_alsoPersistToAppendOnlyTable() {
        when(ops.increment(anyString())).thenReturn(1L);
        svc.loginSuccess(7L, "zhangsan", "10.0.0.9");
        svc.accessDenied("/api/customer/list", 7L, "10.0.0.9");
        svc.permissionChange("role_update", 3L, "行政", 1L);
        svc.userAdminEvent("user_reset_password", 9L, "liming");

        var cap = org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.system.entity.SysAuditLog.class);
        org.mockito.Mockito.verify(auditLogMapper, org.mockito.Mockito.times(4)).insert(cap.capture());
        var types = cap.getAllValues().stream().map(com.aigicrm.module.system.entity.SysAuditLog::getEventType).toList();
        assertTrue(types.contains("LOGIN_SUCCESS") && types.contains("ACCESS_DENIED")
                && types.contains("PERMISSION_CHANGE") && types.contains("USER_ADMIN"),
                "五类事件均须落 append-only 表,实际: " + types);
        var denied = cap.getAllValues().stream()
                .filter(r -> "ACCESS_DENIED".equals(r.getEventType())).findFirst().orElseThrow();
        assertTrue(denied.getDetail().contains("uri=/api/customer/list"));
    }

    /**
     * 【D1=A 2026-10-18】存储配额审批变更审计: actor/申请人/旧值/新值/审批单 五要素俱全,
     * 事件行与 append-only 落库都写 detail。
     */
    @Test
    void storageQuotaChanged_recordsActorApplicantOldNewAndApprovalId() {
        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            svc.storageQuotaChanged(9L, 100L, 200L, 55L);
        }

        assertTrue(messages().stream().anyMatch(m -> m.startsWith("event=STORAGE_QUOTA_CHANGED")
                        && m.contains("userId=\"9\"") && m.contains("operator=\"1\"")
                        && m.contains("actorId=\"1\"") && m.contains("applicantId=\"9\"")
                        && m.contains("oldQuota=\"100\"") && m.contains("newQuota=\"200\"")
                        && m.contains("approvalId=\"55\"")),
                "事件须含 userId/operator/old/new/approvalId, 实际: " + messages());

        var cap = org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.system.entity.SysAuditLog.class);
        org.mockito.Mockito.verify(auditLogMapper, org.mockito.Mockito.atLeastOnce()).insert(cap.capture());
        var row = cap.getAllValues().stream()
                .filter(r -> "STORAGE_QUOTA_CHANGED".equals(r.getEventType())).findFirst().orElseThrow();
        assertTrue(row.getDetail().contains("applicantId=9")
                && row.getDetail().contains("oldQuota=100")
                && row.getDetail().contains("newQuota=200")
                && row.getDetail().contains("approvalId=55"), "落库 detail 缺要素: " + row.getDetail());
    }

    @Test
    void persistFailure_degradesWithVisibleErrorLog_notThrowing() {
        org.mockito.Mockito.doThrow(new RuntimeException("trigger blocked"))
                .when(auditLogMapper).insert(any(com.aigicrm.module.system.entity.SysAuditLog.class));
        assertDoesNotThrow(() -> svc.loginSuccess(7L, "zhangsan", "10.0.0.9"));
        assertTrue(messages().stream().anyMatch(m -> m.contains("event=AUDIT_PERSIST_FAILED")),
                "持久化失败须以 ERROR 级留痕,不静默");
    }

    @Test
    void loginFailBurst_alertFormat() {
        svc.loginFailBurst("zhangsan", "10.0.0.9");
        assertTrue(messages().stream().anyMatch(m ->
                m.contains("event=SECURITY_ALERT") && m.contains("alert=LOGIN_FAIL_BURST")
                        && m.contains("threshold=\"5\"")));
        assertFalse(messages().stream().anyMatch(m -> m.contains("password")), "审计行不得含口令");
    }
}
