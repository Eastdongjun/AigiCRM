package com.aigicrm.config;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.sms")
public class SmsConfig {
    private String signName;
    private String templateCode;

    @Bean
    public Client smsClient(OssConfig ossConfig) throws Exception {
        Config config = new Config()
                .setAccessKeyId(ossConfig.getAccessKeyId())
                .setAccessKeySecret(ossConfig.getAccessKeySecret())
                .setEndpoint("dysmsapi.aliyuncs.com");
        log.info("阿里云短信客户端初始化完成，签名: {}, 模板: {}", signName, templateCode);
        return new Client(config);
    }
}
