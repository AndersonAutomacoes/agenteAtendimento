package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import org.apache.camel.Exchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

final class CamelAuthSupport {

    private CamelAuthSupport() {}

    /**
     * @return tenantId autenticado ou {@code null} se a resposta HTTP já foi preenchida (401/403).
     */
    static String authorizedTenantOrAbort(Exchange exchange, String optionalRequestedTenant) {
        TenantAuthResolution r = TenantAuthResolution.resolve(optionalRequestedTenant);
        if (r.kind() == TenantAuthResolution.Kind.UNAUTHENTICATED) {
            exchange.getIn().setBody(new IngestErrorResponse("não autenticado"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.UNAUTHORIZED.value());
            return null;
        }
        if (r.kind() == TenantAuthResolution.Kind.FORBIDDEN) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId não coincide com a sessão"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return null;
        }
        return r.tenantId();
    }
}
