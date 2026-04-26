package com.atendimento.cerebro;

import com.atendimento.cerebro.infrastructure.config.AnalyticsCategorizationProperties;
import com.atendimento.cerebro.infrastructure.config.AnalyticsIntentClassificationProperties;
import com.atendimento.cerebro.infrastructure.config.CerebroAppointmentConfirmationProperties;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import com.atendimento.cerebro.infrastructure.config.FirebaseProperties;
import com.atendimento.cerebro.infrastructure.config.GroqSttProperties;
import com.atendimento.cerebro.infrastructure.config.ChatAnalyticsProperties;
import com.atendimento.cerebro.infrastructure.config.InviteEmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.atendimento.cerebro")
@EnableScheduling
@EnableConfigurationProperties({
    AnalyticsCategorizationProperties.class,
    AnalyticsIntentClassificationProperties.class,
    ChatAnalyticsProperties.class,
    FirebaseProperties.class,
    CerebroGoogleCalendarProperties.class,
    CerebroAppointmentConfirmationProperties.class,
    InviteEmailProperties.class,
    GroqSttProperties.class
})
public class CerebroApplication {

    public static void main(String[] args) {
        SpringApplication.run(CerebroApplication.class, args);
    }
}
