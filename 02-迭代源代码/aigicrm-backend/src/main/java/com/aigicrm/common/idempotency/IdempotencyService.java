package com.aigicrm.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 【PR-05 残余 2026-09-21】提交幂等护栏:Idempotency-Key + Redis PENDING/RESULT 两段式。
 * <p>语义(调用方约定,以 POST /customer/full 为首个接入点):
 * <ul>
 *   <li>{@link #tryBegin}: {@code SET key PENDING NX EX 30} —— 抢占执行权,同键并发只有一个执行者</li>
 *   <li>{@link #finish}: 写入结果并续期 24h —— 后续同键请求直接取结果,不再重复建客</li>
 *   <li>{@link #abort}: 业务失败后 DEL 释放 —— 允许用户立即重试</li>
 *   <li>{@link #awaitResult}: 并发同键请求轮询(PENDING→结果),超时返回 null</li>
 * </ul>
 * 降级口径与 SecurityAuditService 一致:Redis 任何异常只 debug 记录并按"无幂等"放行,
 * <b>绝不阻断业务</b>(幂等是护栏,不是依赖)。
 * 键作用域由服务端拼接(前缀+userId),客户端只提供随机串,防跨用户命中他人结果。
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /** 占位值:执行中(SET NX 抢占写入),与真实结果(数字串)区分 */
    static final String PENDING = "__PENDING__";

    private final StringRedisTemplate redis;

    /**
     * 【GPT复审 P1-07 2026-09-21】Redis 故障时的降级模式(产品权衡项, 配置化):
     * bypass(默认, 保持 PR-05 原设计)= 降级放行, 可用性优先, 极端场景可能重复创建;
     * strict = fail-closed(与 PR-01 授权异常口径一致), 幂等基础设施不可用时拒绝写入, 绝不重复优先。
     * 生产可在部署 env 设 IDEMPOTENCY_FAIL_MODE=strict 切换。
     */
    @org.springframework.beans.factory.annotation.Value("${aigicrm.idempotency.fail-mode:bypass}")
    private String failMode;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 合法客户端键:8..128 位可见 ASCII(客户端生成随机 UUID 即可);过短/含控制字符/超长一律忽略 */
    public boolean isValidKey(String key) {
        return key != null && key.length() >= 8 && key.length() <= 128
                && key.chars().allMatch(c -> c >= 0x21 && c <= 0x7e);
    }

    /** 服务端作用域键:跨前缀、跨用户互不命中 */
    public String scope(String prefix, Long userId, String key) {
        return "idem:" + prefix + ":" + userId + ":" + key;
    }

    /** 抢占执行权;Redis 异常时按 fail-mode 降级: bypass=当作抢到(幂等降级为无), strict=拒绝(503) */
    public boolean tryBegin(String scopedKey) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(scopedKey, PENDING, Duration.ofSeconds(30)));
        } catch (Exception e) {
            if ("strict".equalsIgnoreCase(failMode)) {
                log.warn("idempotency tryBegin fail-closed(strict): {}", e.getMessage());
                throw new com.aigicrm.common.BusinessException(503, "幂等服务暂不可用，请稍后重试");
            }
            log.warn("idempotency tryBegin degrade(fail-open/bypass): {}", e.getMessage());
            return true;
        }
    }

    /** 记录结果(24h);失败只记 debug —— 已建成的客户不受影响,后续同键最多退化为重复判断缺失 */
    public void finish(String scopedKey, String value) {
        try {
            redis.opsForValue().set(scopedKey, value, Duration.ofHours(24));
        } catch (Exception e) {
            log.debug("idempotency finish degrade: {}", e.getMessage());
        }
    }

    /** 释放(业务失败后),允许立即重试 */
    public void abort(String scopedKey) {
        try {
            redis.delete(scopedKey);
        } catch (Exception e) {
            log.debug("idempotency abort degrade: {}", e.getMessage());
        }
    }

    /**
     * 读取/等待结果:waitMs=0 即单次快查。
     * 返回 null 表示"暂无结果"(PENDING 中 / 超时 / Redis 不可用),调用方自行降级放行。
     */
    public String awaitResult(String scopedKey, long waitMs) {
        long deadline = System.currentTimeMillis() + waitMs;
        try {
            while (true) {
                String v = redis.opsForValue().get(scopedKey);
                if (v != null && !PENDING.equals(v)) {
                    return v;
                }
                if (System.currentTimeMillis() >= deadline) {
                    return null;
                }
                Thread.sleep(80);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("idempotency awaitResult degrade: {}", e.getMessage());
            return null;
        }
    }
}
