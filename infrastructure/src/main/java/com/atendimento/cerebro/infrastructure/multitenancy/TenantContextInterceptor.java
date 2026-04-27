package com.atendimento.cerebro.infrastructure.multitenancy;

import com.atendimento.cerebro.infrastructure.config.CerebroMultitenancyProperties;
import java.util.Locale;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Interceptor Camel (primeiro passo do webhook): captura o header configurável (ex.: {@code X-AxeZap-Tenant}) para
 * resolução do tenant em conjunto com {@link WebhookTenantResolver}.
 */
@Component
public class TenantContextInterceptor implements Processor {

    public static final String EXCHANGE_PROP_TENANT_HEADER_HINT = "cerebroTenantHeaderHint";

    private final CerebroMultitenancyProperties multitenancyProperties;

    public TenantContextInterceptor(CerebroMultitenancyProperties multitenancyProperties) {
        this.multitenancyProperties = multitenancyProperties;
    }

    @Override
    public void process(Exchange exchange) {
        exchange.setProperty(EXCHANGE_PROP_TENANT_HEADER_HINT, null);
        String name = multitenancyProperties.getHeaderName();
        String v = firstHeader(exchange, name);
        if (v != null && !v.isBlank()) {
            exchange.setProperty(EXCHANGE_PROP_TENANT_HEADER_HINT, v.strip());
        }
    }

    private static String firstHeader(Exchange exchange, String logicalName) {
        String v = exchange.getIn().getHeader(logicalName, String.class);
        if (v != null && !v.isBlank()) {
            return v;
        }
        if (logicalName != null) {
            v = exchange.getIn().getHeader(logicalName.toLowerCase(Locale.ROOT), String.class);
            if (v != null && !v.isBlank()) {
                return v;
            }
            v = exchange.getIn().getHeader("CamelHttpHeader." + logicalName, String.class);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
