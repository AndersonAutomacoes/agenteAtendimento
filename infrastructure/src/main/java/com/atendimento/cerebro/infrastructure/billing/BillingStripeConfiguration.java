package com.atendimento.cerebro.infrastructure.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingStripeProperties.class)
public class BillingStripeConfiguration {}
