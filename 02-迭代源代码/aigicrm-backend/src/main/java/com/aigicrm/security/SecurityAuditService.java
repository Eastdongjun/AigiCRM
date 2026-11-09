package com.aigicrm.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.aigicrm.module.system.entity.SysAuditLog;
import com.aigicrm.module.system.mapper.SysAuditLogMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 【BS-A09 2026-09-19】结构化安全审计日志 + 阈值告警。
 *
 * <p>背景(第二轮深度验证报告 §3.2 / STRIDE-A09 盲区):匿名链路对外中性化
 * (P1-06)之后,内部必须留有足以检测枚举/爆破的证据;越权拒绝与权限/集团
 * 变更此前只有零散 warn,不可聚合告警。</p>
 *
 * <ul>
 *   <li><b>结构化事件</b>:统一走 logger {@code SECURITY_AUDIT},格式
 *       {@code event=NAME k=v k=v ...},可被日志平台直接抽取聚合;</li>
 *   <li><b>阈值告警</b>:独立 Redis 计数窗口(与业务限流键互不干扰),达到阈值
 *       追加一条 {@code event=SECURITY_ALERT ...}(WARN 级)——告警只负责"看得见",
 *       拦截仍归业务限流,职责分离;</li>
 *   <li><b>降级不阻断</b>:审计/计数任何异常(含 Redis 不可用)只 debug 记录并跳过,
 *       绝不向业务链路抛出——授权路径的 fail-closed 由各业务点自己保证,审计是旁路。</li>
 * </ul>
 *
 * <p>禁止在本类做任何拦截/拒绝决策。</p>
 */
@Slf4j(topic = "SECURITY_AUDIT")
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final StringRedisTemplate redis;
    private final SysAuditLogMapper auditLogMapper;

    /** 阈值(窗口内次数,达到即告警一次;窗口=告警计数窗口)。public:业务方(login)复用同阈值判断跨界 */
    public static final int LOGIN_FAIL_ALERT = 5;
    public static final int LOGIN_FAIL_IP_ALERT = 15;
    static final int ANON_PROBE_IP_ALERT = 10;
    static final int ACCESS_DENIED_IP_ALERT = 30;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String ALERT_KEY_PREFIX = "audit:alert:";

    /** 登录失败(含账号不存在/停用/密码错误,对外统一中性文案;内部如实区分) */
    public void loginFailed(String username, String clientIp, boolean userExists, boolean enabled,
                            int failCount, int ipFailCount) {
        event("LOGIN_FAILED", "username", username, "ip", clientIp,
                "userExists", userExists, "enabled", enabled,
                "failCount", failCount, "ipFailCount", ipFailCount);
        persist("LOGIN_FAILED", null, username, clientIp,
                "userExists=" + userExists + " enabled=" + enabled
                        + " failCount=" + failCount + " ipFailCount=" + ipFailCount);
    }

    /** 登录成功(审计基线:爆破检测需要"失败而无成功"与"正常用户偶发失败"可区分) */
    public void loginSuccess(Long userId, String username, String clientIp) {
        event("LOGIN_SUCCESS", "userId", userId, "username", username, "ip", clientIp);
        persist("LOGIN_SUCCESS", userId, username, clientIp, null);
    }

    /**
     * 匿名链路探测(忘记密码 lookup/send-code/reset)——对外中性响应,内部如实记录。
     * 达到 ANON_PROBE_IP_ALERT 时告警:中性化后枚举攻击唯一可见的痕迹就在这里。
     */
    public void anonymousProbe(String kind, String username, String clientIp) {
        event("ANON_PROBE", "kind", kind, "username", username, "ip", clientIp);
        persist("ANON_PROBE", null, username, clientIp, "kind=" + kind);
        thresholdAlert("ANON_PROBE_IP_BURST", "anon:" + clientIp, ANON_PROBE_IP_ALERT,
                "kind", kind, "ip", clientIp);
    }

    /** 越权拒绝(403):Spring Security @PreAuthorize 拒绝统一经 AccessDeniedHandler 汇入 */
    public void accessDenied(String uri, Long userId, String clientIp) {
        event("ACCESS_DENIED", "uri", uri, "userId", userId, "ip", clientIp);
        persist("ACCESS_DENIED", userId, null, clientIp, "uri=" + uri);
        thresholdAlert("ACCESS_DENIED_IP_BURST", "denied:" + clientIp, ACCESS_DENIED_IP_ALERT,
                "uri", uri, "ip", clientIp);
    }

    /** 权限变更(角色增删改,含菜单/权限码绑定调整) */
    public void permissionChange(String action, Long targetRoleId, String roleName, Long actorId) {
        event("PERMISSION_CHANGE", "action", action, "roleId", targetRoleId, "roleName", roleName, "actorId", actorId);
        persist("PERMISSION_CHANGE", actorId, null, null,
                "action=" + action + " roleId=" + targetRoleId + " roleName=" + roleName);
    }

    /**
     * 【体检P4 2026-11-09】权限等价写操作通用审计——非角色维度的权限写路径此前零留痕:
     * 数据范围保存/公海池名单/文件夹ACL/文件空间类型默认。targetDesc 描述目标
     * (如 "scope:ROLE:06" / "seaPool:3" / "folderAcl:7" / "folderTypeDefault:public")。
     */
    public void permissionChange(String action, String targetDesc) {
        Long actorId = null;
        try {
            actorId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignore) {
            // 无会话上下文(内部调用)时 actor 为空
        }
        event("PERMISSION_CHANGE", "action", action, "target", targetDesc, "actorId", actorId);
        persist("PERMISSION_CHANGE", actorId, null, null, "action=" + action + " " + targetDesc);
    }

    /** 集团/租户变更:会话切换 */
    public void groupSwitch(Long userId, String username, Long fromGroupId, Long toGroupId) {
        event("GROUP_SWITCH", "userId", userId, "username", username,
                "from", fromGroupId, "to", toGroupId);
        persist("GROUP_SWITCH", userId, username, null,
                "from=" + fromGroupId + " to=" + toGroupId);
    }

    /** 【BS-R】用户管理动作(管理员自身操作被审计):create/update/delete/resetPassword/markLeave 等 */
    public void userAdminEvent(String action, Long targetUserId, String targetUsername) {
        Long actorId = null;
        try {
            actorId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignore) {
            // 无会话上下文(如内部调用)时 actor 为空
        }
        event("USER_ADMIN", "action", action, "targetUserId", targetUserId,
                "targetUsername", targetUsername, "actorId", actorId);
        persist("USER_ADMIN", actorId, targetUsername, null,
                "action=" + action + " targetUserId=" + targetUserId);
    }

    /** 集团/租户变更:用户归属组织调整(影响租户边界推导) */
    public void userOrgChanged(Long userId, Long oldOrgId, Long newOrgId, Long actorId) {
        event("USER_ORG_CHANGED", "userId", userId, "from", oldOrgId, "to", newOrgId, "actorId", actorId);
    }

    /**
     * 【11-08 修复清单5】激励专项授权保存(upsert biz_incentive_grant)审计留痕。
     * actor=当前操作人(与 LOGIN_SUCCESS 同款 persist 旁路落库 sys_audit_log, 失败降级不阻断授权);
     * detail 记目标 user_id + 四 flag 终态 JSON(与 sys_rule_version 的时间线互补, 本表面向操作人审计)。
     */
    public void incentiveGrantSaved(Long targetUserId, int canViewShared, int canViewAll,
                                    int canEdit, int canDelete) {
        Long actorId = null;
        try {
            actorId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignore) {
            // 无会话上下文(如内部调用)时 actor 为空
        }
        event("INCENTIVE_GRANT_SAVE", "targetUserId", targetUserId,
                "canViewShared", canViewShared, "canViewAll", canViewAll,
                "canEdit", canEdit, "canDelete", canDelete, "actorId", actorId);
        persist("INCENTIVE_GRANT_SAVE", actorId, null, null,
                "targetUserId=" + targetUserId
                        + " flags={\"canViewShared\":" + canViewShared
                        + ",\"canViewAll\":" + canViewAll
                        + ",\"canEdit\":" + canEdit
                        + ",\"canDelete\":" + canDelete + "}");
    }

    /**
     * 【D1=A 2026-10-18】存储空间配额审批变更审计:
     * actorId=审批操作人(执行动作的当前登录人), applicantId=被调整用户,
     * old/new=调整前后配额(bytes), approvalId=来源审批单。
     * 事件与 append-only 落库均为旁路, 失败不阻断配额变更业务。
     */
    public void storageQuotaChanged(Long applicantId, Long oldQuota, Long newQuota, Long approvalId) {
        Long actorId = null;
        try {
            actorId = SecurityUtils.getCurrentUserId();
        } catch (Exception ignore) {
            // 无会话上下文(如内部调用)时 actor 为空
        }
        event("STORAGE_QUOTA_CHANGED", "userId", applicantId, "applicantId", applicantId,
                "oldQuota", oldQuota, "newQuota", newQuota, "approvalId", approvalId,
                "operator", actorId, "actorId", actorId);
        persist("STORAGE_QUOTA_CHANGED", actorId, null, null,
                "userId=" + applicantId + " applicantId=" + applicantId + " oldQuota=" + oldQuota
                        + " newQuota=" + newQuota + " approvalId=" + approvalId
                        + " operator=" + actorId);
    }

    /** 登录失败计数已达告警线(计数由 AuthService 限流逻辑持有,此处只负责告警) */
    public void loginFailBurst(String username, String clientIp) {
        alert("LOGIN_FAIL_BURST", "username", username, "ip", clientIp, "threshold", LOGIN_FAIL_ALERT);
    }

    public void loginFailIpBurst(String clientIp) {
        alert("LOGIN_FAIL_IP_BURST", "ip", clientIp, "threshold", LOGIN_FAIL_IP_ALERT);
    }

    // ===== 内部:事件/告警/阈值 =====

    private void event(String name, Object... kv) {
        try {
            log.info("event={} {}", name, format(kv));
        } catch (Exception e) {
            degrade(e);
        }
    }

    private void alert(String name, Object... kv) {
        try {
            log.warn("event=SECURITY_ALERT alert={} {}", name, format(kv));
        } catch (Exception e) {
            degrade(e);
        }
    }

    /** 窗口计数达到阈值时告警恰好一次(== 判断:只在跨界那次触发) */
    private void thresholdAlert(String alertName, String counterKey, int threshold, Object... kv) {
        try {
            String key = ALERT_KEY_PREFIX + counterKey;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            if (count != null && count == threshold) {
                alert(alertName, kv);
            }
        } catch (Exception e) {
            degrade(e);
        }
    }

    /**
     * 【BS-R】事件旁路落库(append-only 表)。失败降级不阻断业务,但以 ERROR 级文件日志
     * 留痕——审计缺口可见,不静默吞掉。
     */
    private void persist(String eventType, Long actorId, String username, String ip, String detail) {
        try {
            SysAuditLog row = new SysAuditLog();
            row.setEventType(eventType);
            row.setActorId(actorId);
            row.setActorUsername(username != null && username.length() > 64
                    ? username.substring(0, 64) : username);
            row.setIp(ip);
            row.setDetail(detail != null && detail.length() > 1000
                    ? detail.substring(0, 1000) : detail);
            auditLogMapper.insert(row);
        } catch (Exception e) {
            log.error("event=AUDIT_PERSIST_FAILED type={} error={}", eventType, e.getMessage());
        }
    }

    private static String format(Object... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            sb.append(kv[i]).append('=').append('\"').append(kv[i + 1]).append("\" ");
        }
        return sb.toString().trim();
    }

    private void degrade(Exception e) {
        // 审计旁路降级:不影响业务链路(Redis 抖动等),但留下痕迹便于排查审计缺口
        log.debug("security audit degraded: {}", e.getMessage());
    }
}
