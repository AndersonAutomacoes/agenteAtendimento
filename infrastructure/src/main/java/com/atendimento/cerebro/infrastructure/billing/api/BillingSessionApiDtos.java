package com.atendimento.cerebro.infrastructure.billing.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class BillingSessionApiDtos {

    private BillingSessionApiDtos() {}

    public record CheckoutSessionRequest(String tenantId, String priceId) {}

    public record PortalSessionRequest(String tenantId) {}

    public record SessionUrlResponse(String url) {}

    public record ErrorResponse(String error) {}
}
