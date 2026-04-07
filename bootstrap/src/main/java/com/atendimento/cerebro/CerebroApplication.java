package com.atendimento.cerebro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.atendimento.cerebro")
public class CerebroApplication {

    public static void main(String[] args) {
        SpringApplication.run(CerebroApplication.class, args);
    }
}
