package com.ycmn;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.ycmn.module.*.mapper")
@EnableScheduling
public class YcmnApplication {
    public static void main(String[] args) {
        SpringApplication.run(YcmnApplication.class, args);
    }
}
