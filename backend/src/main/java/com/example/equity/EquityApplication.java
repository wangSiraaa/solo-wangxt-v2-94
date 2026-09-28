package com.example.equity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 虚构股权计划的归属 / 行权核对系统。
 * 规则均为演示用虚构计划，不构成税务或投资建议。
 */
@SpringBootApplication
public class EquityApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquityApplication.class, args);
    }
}
