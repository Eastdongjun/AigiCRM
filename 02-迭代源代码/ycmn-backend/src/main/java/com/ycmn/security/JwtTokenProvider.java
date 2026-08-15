package com.ycmn.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.remember-expiration}")
    private long rememberExpiration;

    private final RedisTemplate<String, Object> redisTemplate;
    /** 【2026-08-14 安全 P0】撤销落库兜底：Redis 故障时黑名单改走 DB 查询，撤销不再 fail-open */
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    /** 下载专用 token 有效期(1h)：够列表浏览/导出窗口，泄漏影响面有界 */
    private static final long DOWNLOAD_TOKEN_TTL_MS = 3600_000L;

    @PostConstruct
    public void init() {
        if ("dev-jwt-secret-do-not-use-in-production".equals(secret)) {
            log.warn("============================================");
            log.warn("⚠️  使用了开发环境默认 JWT 密钥，不安全！");
            log.warn("⚠️  生产环境请设置环境变量 JWT_SECRET");
            log.warn("============================================");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long userId, String username, boolean rememberMe) {
        return generateToken(userId, username, rememberMe, null);
    }

    /** 【批④ 2026-08-10】带会话集团签发(多集团: 每个 token 独立当前集团, 切换=重签) */
    public String generateToken(Long userId, String username, boolean rememberMe, Long currentGroupId) {
        long exp = rememberMe ? rememberExpiration : expiration;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + exp);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti: 会话撤销/黑名单用
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiryDate);
        if (currentGroupId != null) {
            builder.claim("currentGroupId", currentGroupId);
        }
        return builder.signWith(getSigningKey()).compact();
    }

    /** 【批④】token 携带的会话集团(旧 token 无 claim 返回 null, 由调用方回退最小集团) */
    public Long getCurrentGroupIdFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            Object v = claims.get("currentGroupId");
            return v == null ? null : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    // ==================== 会话撤销/黑名单 (M2 2026-08-10) ====================

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    /** token 的 jti 声明(无则 null) */
    public String getJtiFromToken(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getId() : null;
    }

    /** jti 是否已被拉黑(注销后的会话立即失效)。
     *  【2026-08-14 安全 P0】DB 为权威判定：Redis 命中直接 true；miss/故障一律回查 DB 黑名单表——
     *  消除"Redis 重启/淘汰后 DB 黑名单行被忽略、已撤销 token 复活"的 fail-open 窗口（评审 P1-2 修复） */
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("isBlacklisted Redis 查询失败，回退 DB: {}", e.getMessage());
        }
        // Redis miss/异常 → DB 权威判定（uk_jti 索引命中，jti 行仅存活至 token 过期，代价可控）
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_token_blacklist WHERE jti=? AND expires_at>NOW()",
                    Integer.class, jti);
            return n != null && n > 0;
        } catch (Exception dbEx) {
            log.error("isBlacklisted DB 查询失败(撤销链路不可用): {}", dbEx.getMessage());
            return false;
        }
    }

    /** 拉黑一个 token(按 jti, TTL=剩余有效期)——注销即失效, 被盗 token 无法复用。
     *  【2026-08-14 安全 P0】DB 落库为主、Redis 为辅：落库失败视为撤销失败(抛异常, 不可静默) */
    public void blacklistToken(String token) {
        try {
            Claims claims = getClaims(token);
            String jti = claims.getId();
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (jti == null || ttlMs <= 0) return;
            try {
                jdbc.update("INSERT INTO sys_token_blacklist (jti, user_id, expires_at) VALUES (?,?,?) " +
                                "ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)",
                        jti, Long.parseLong(claims.getSubject()),
                        new java.sql.Timestamp(claims.getExpiration().getTime()));
                // 顺带清理过期行，防表无限增长
                jdbc.update("DELETE FROM sys_token_blacklist WHERE expires_at < NOW()");
            } catch (Exception dbEx) {
                log.error("拉黑 token 落库失败(撤销未完成): {}", dbEx.getMessage());
                throw new RuntimeException("注销失败：撤销服务暂不可用，请稍后重试", dbEx);
            }
            try {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofMillis(ttlMs));
            } catch (Exception e) {
                log.warn("拉黑 token Redis 写失败(已落库, DB 兜底生效): {}", e.getMessage());
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            log.error("解析 token 失败，无法拉黑: {}", e.getMessage());
        }
    }

    /** 短时下载专用 token：scope=download，1h 有效（<img> 缩略图/导出无法带 header，避免主 JWT 进 URL/日志） */
    public String generateDownloadToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // 【修复 2026-08-10 P1-07】补充 jti，黑名单机制对下载 token 生效
                .subject(String.valueOf(userId))
                .claim("scope", "download")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + DOWNLOAD_TOKEN_TTL_MS))
                .signWith(getSigningKey())
                .compact();
    }

    // ==================== 改密后旧 token 失效 (P2-13 2026-08-10) ====================

    private static final String PWD_CHANGED_PREFIX = "jwt:pwd-changed:";
    /** 改密标记 TTL：覆盖"记住我"最长有效期，防 Redis 残留永久失效 */
    private static final long PWD_CHANGED_TTL_MS = 8L * 24 * 3600_000L;

    /** 记录用户密码变更时间(变更后所有早于该时刻签发的 token 立即失效)。
     *  【2026-08-14 安全 P0】Redis 写失败不再静默——撤销的权威依据已下沉到 sys_user.pwd_changed_at(DB 校验, JwtAuthFilter) */
    public void markPasswordChanged(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.opsForValue().set(PWD_CHANGED_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()), Duration.ofMillis(PWD_CHANGED_TTL_MS));
        } catch (Exception e) {
            log.error("markPasswordChanged Redis 写失败(已由 DB pwd_changed_at 兜底撤销): {}", e.getMessage());
        }
    }

    /** token 是否因"改密时间晚于签发时间"而失效(Redis 故障降级为不失效，与黑名单降级一致) */
    public boolean isInvalidatedByPasswordChange(String token) {
        try {
            Claims claims = getClaims(token);
            Long userId = Long.parseLong(claims.getSubject());
            Date iat = claims.getIssuedAt();
            if (iat == null) return false;
            Object changedAt = redisTemplate.opsForValue().get(PWD_CHANGED_PREFIX + userId);
            if (changedAt == null) return false;
            long changed = Long.parseLong(String.valueOf(changedAt));
            // 【修复 2026-08-10 P3-05】changed 毫秒精度 vs iat 秒精度: 下取整到秒, 防"改密后同秒内新登录被误判失效"
            changed = changed / 1000 * 1000;
            return iat.getTime() < changed;
        } catch (Exception e) {
            log.warn("isInvalidatedByPasswordChange 检查失败(降级不失效): {}", e.getMessage());
            return false;
        }
    }

    /** token 的签发时刻（解析失败返回 null；用于 DB 级改密撤销校验） */
    public java.util.Date getIssuedAtFromToken(String token) {
        try {
            return getClaims(token).getIssuedAt();
        } catch (Exception e) {
            return null;
        }
    }

    /** token 的 scope 声明（无则返回 null） */
    public String getScopeFromToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
            return claims.get("scope", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
