package com.aigicrm.architecture;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 【2026-10-08 生产事故钉】application-prod.yml 曾因另起重复 spring: 顶层键导致
 * SnakeYAML "found duplicate key" 解析失败、生产后端重启循环——测试与 CI 均不激活
 * prod 档，重复键对它们不可见，只有生产启动才炸。本测试用与 Spring 相同的
 * SafeConstructor 语义直接解析两个 profile 文件，把该类错误挡在 CI。
 */
class ProfileYamlParseTest {

    @Test
    void applicationYaml_hasNoDuplicateTopLevelKeys() {
        parseStrict("application.yml");
    }

    @Test
    void applicationProdYaml_hasNoDuplicateTopLevelKeys() {
        parseStrict("application-prod.yml");
    }

    private void parseStrict(String name) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            assert in != null : name + " 不存在于测试 classpath";
            // Yaml 默认 SafeConstructor 的 processDuplicateKeys 对重复键抛异常，与启动路径一致
            assertDoesNotThrow(() -> new Yaml().load(in));
        } catch (Exception e) {
            throw new IllegalStateException(name + " 读取失败", e);
        }
    }
}
