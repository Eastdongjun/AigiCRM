package com.ycmn.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * 带多态白名单的 JSON Redis 序列化器。
 *
 * 【需求】Boot 4.1 升级后 Jackson2JsonRedisSerializer / GenericJackson2JsonRedisSerializer
 *         均被弃用并标记移除,需要一个零弃用、且保留 M5 多态白名单的替代实现
 *         (RedisSerializer.json() 工厂使用自带 mapper,会丢失项目级白名单,不可用)
 * 【方法】直接实现 RedisSerializer&lt;Object&gt;,序列化/反序列化完全委托给注入的 ObjectMapper
 *         (该 mapper 已 activateDefaultTyping + BasicPolymorphicTypeValidator 白名单,
 *          JSON 含 @class 类型信息,反序列化按 Object.class 读回即可)
 * 【业务规则】与旧 Jackson2JsonRedisSerializer 的线上格式完全一致(同一 mapper、同一 typing),
 *         存量缓存无需清理;null 输入按 RedisSerializer 惯例返回空字节数组/反序列化返回 null
 * 【参数】mapper 已配置类型白名单的 ObjectMapper
 * 【注意】不可用 RedisSerializer.json() 替代——其默认 typing 无白名单,属安全回退(M5)
 */
public class WhitelistedJsonRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper mapper;

    /** @param mapper 已配置多态白名单的 ObjectMapper(见 RedisConfig) */
    public WhitelistedJsonRedisSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 序列化:null → 空字节数组;否则 mapper 写出 JSON(含 @class 类型信息) */
    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) return new byte[0];
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException e) {
            throw new SerializationException("Redis JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /** 反序列化:null/空 → null;否则按 Object.class 读回(依赖 JSON 内嵌 @class) */
    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return mapper.readValue(bytes, Object.class);
        } catch (IOException e) {
            throw new SerializationException("Redis JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
