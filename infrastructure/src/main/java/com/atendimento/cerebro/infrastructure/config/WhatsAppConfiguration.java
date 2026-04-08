package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.infrastructure.whatsapp.WhatsAppTenantLookupAdapter;
import com.atendimento.cerebro.infrastructure.whatsapp.WhatsAppTenantLookupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WhatsAppTenantLookupProperties.class)
public class WhatsAppConfiguration {

    @Bean
    public WhatsAppTenantLookupPort whatsAppTenantLookupPort(WhatsAppTenantLookupProperties properties) {
        return new WhatsAppTenantLookupAdapter(properties);
    }
}
