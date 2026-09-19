package com.aigicrm.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    }

    /** 登录成功(审计基线:爆破检测需要"失败而无成功"与"正常用户偶发失败"可区分) */
    public void loginSuccess(Long userId, String username, String clientIp) {
        event("LOGIN_SUCCESS", "userId", userId, "username", username, "ip", clientIp);
    }

    /**
     * 匿名链路探测(忘记密码 lookup/send-code/reset)——对外中性响应,内部如实记录。
     * 达到 ANON_PROBE_IP_ALERT 时告警:中性化后枚举攻击唯一可见的痕迹就在这里。
     */
    public void anonymousProbe(String kind, String username, String clientIp) {
        event("ANON_PROBE", "kind", kind, "username", username, "ip", clientIp);
        thresholdAlert("ANON_PROBE_IP_BURST", "anon:" + clientIp, ANON_PROBE_IP_ALERT,
                "kind", kind, "ip", clientIp);
    }

    /** 越权拒绝(403):Spring Security @PreAuthorize 拒绝统一经 AccessDeniedHandler 汇入 */
    public void accessDenied(String uri, Long userId, String clientIp) {
        event("ACCESS_DENIED", "uri", uri, "userId", userId, "ip", clientIp);
        thresholdAlert("ACCESS_DENIED_IP_BURST", "denied:" + clientIp, ACCESS_DENIED_IP_ALERT,
                "uri", uri, "ip", clientIp);
    }

    /** 权限变更(角色增删改,含菜单/权限码绑定调整) */
    public void permissionChange(String action, Long targetRoleId, String roleName, Long actorId) {
        event("PERMISSION_CHANGE", "action", action, "roleId", targetRoleId, "roleName", roleName, "actorId", actorId);
    }

    /** 集团/租户变更:会话切换 */
    public void groupSwitch(Long userId, String username, Long fromGroupId, Long toGroupId) {
        event("GROUP_SWITCH", "userId", userId, "username", username,
                "from", fromGroupId, "to", toGroupId);
    }

    /** 集团/租户变更:用户归属组织调整(影响租户边界推导) */
    public void userOrgChanged(Long userId, Long oldOrgId, Long newOrgId, Long actorId) {
        event("USER_ORG_CHANGED", "userId", userId, "from", oldOrgId, "to", newOrgId, "actorId", actorId);
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
