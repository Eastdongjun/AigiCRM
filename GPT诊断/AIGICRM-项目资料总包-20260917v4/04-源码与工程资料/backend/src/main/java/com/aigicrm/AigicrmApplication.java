package com.aigicrm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.aigicrm.module.*.mapper")
@EnableScheduling
public class AigicrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(AigicrmApplication.class, args);
    }
}
