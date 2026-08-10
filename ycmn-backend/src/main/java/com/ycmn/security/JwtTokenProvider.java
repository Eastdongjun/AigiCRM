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
import java.util.concurrent.TimeUnit;

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
        long exp = rememberMe ? rememberExpiration : expiration;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + exp);

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti: 会话撤销/黑名单用
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
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

    /** jti 是否已被拉黑(注销后的会话立即失效) */
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            // 【修复 2026-08-10 P2-12】Redis 故障时降级为"不拉黑"并告警，
            // 避免全站带 token 请求 500(与 PermissionCacheService.get 降级语义一致)
            log.warn("isBlacklisted 查询失败(降级不拉黑): {}", e.getMessage());
            return false;
        }
    }

    /** 拉黑一个 token(按 jti, TTL=剩余有效期)——注销即失效, 被盗 token 无法复用 */
    public void blacklistToken(String token) {
        try {
            Claims claims = getClaims(token);
            String jti = claims.getId();
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (jti != null && ttlMs > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", ttlMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("拉黑 token 失败: {}", e.getMessage());
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

    /** 记录用户密码变更时间(变更后所有早于该时刻签发的 token 立即失效) */
    public void markPasswordChanged(Long userId) {
        if (userId == null) return;
        try {
            redisTemplate.opsForValue().set(PWD_CHANGED_PREFIX + userId,
                    String.valueOf(System.currentTimeMillis()), PWD_CHANGED_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("markPasswordChanged 失败(降级: 旧 token 继续有效): {}", e.getMessage());
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
