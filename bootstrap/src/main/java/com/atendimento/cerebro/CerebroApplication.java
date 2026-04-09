package com.atendimento.cerebro;

import com.atendimento.cerebro.infrastructure.config.AnalyticsCategorizationProperties;
import com.atendimento.cerebro.infrastructure.config.AnalyticsIntentClassificationProperties;
import com.atendimento.cerebro.infrastructure.config.ChatAnalyticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.atendimento.cerebro")
@EnableScheduling
@EnableConfigurationProperties({
    AnalyticsCategorizationProperties.class,
    AnalyticsIntentClassificationProperties.class,
    ChatAnalyticsProperties.class
})
public class CerebroApplication {

    public static void main(String[] args) {
        SpringApplication.run(CerebroApplication.class, args);
    }
}
