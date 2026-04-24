package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.domain.tenant.TenantId;

/**
 * Texto simples para envio WhatsApp (Evolution {@code sendText} via rotas Camel).
 *
 * @param number apenas dígitos, com DDI (ex.: {@code 5511999000000}).
 */
public record WhatsAppTextPayload(TenantId tenantId, String number, String text) {}
