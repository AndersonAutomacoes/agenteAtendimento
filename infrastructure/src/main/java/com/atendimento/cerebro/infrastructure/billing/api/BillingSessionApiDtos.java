package com.atendimento.cerebro.infrastructure.billing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class BillingSessionApiDtos {

    private BillingSessionApiDtos() {}

    public record CheckoutSessionRequest(String tenantId, String priceId) {}

    public record PortalSessionRequest(String tenantId) {}

    public record SessionUrlResponse(String url) {}

    /** Preço público para renderização da página de planos (sem dados sensíveis). */
    public record PublicPriceResponse(String tier, String interval, String priceId, String currency, Long unitAmount) {}

    public record PublicPricesResponse(List<PublicPriceResponse> prices) {}

    public record ErrorResponse(String error) {}
}
