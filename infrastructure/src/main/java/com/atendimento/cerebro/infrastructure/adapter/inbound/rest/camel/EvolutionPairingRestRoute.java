package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.service.EvolutionTenantProvisioningService;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Solicita novo QR Evolution para tenants que já configuraram {@code whatsapp_provider_type = EVOLUTION}.
 */
@Component
@Order(104)
public class EvolutionPairingRestRoute extends RouteBuilder {

    private final EvolutionTenantProvisioningService evolutionTenantProvisioningService;
    private final TenantConfigurationStorePort tenantConfigurationStore;

    public EvolutionPairingRestRoute(
            EvolutionTenantProvisioningService evolutionTenantProvisioningService,
            TenantConfigurationStorePort tenantConfigurationStore) {
        this.evolutionTenantProvisioningService = evolutionTenantProvisioningService;
        this.tenantConfigurationStore = tenantConfigurationStore;
    }

    @Override
    public void configure() {
        rest("/v1/tenant/whatsapp/evolution/pairing")
                .post()
                .bindingMode(RestBindingMode.off)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(EvolutionPairingHttpResponse.class)
                .to("direct:evolutionPairing");

        from("direct:evolutionPairing")
                .routeId("evolutionPairing")
                .process(this::handlePairing);
    }

    private void handlePairing(Exchange exchange) {
        String tenantParam =
                parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        String tenantHeader = exchange.getMessage().getHeader("tenantId", String.class);
        String requested = tenantParam != null ? tenantParam : tenantHeader;

        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        try {
            var qr =
                    evolutionTenantProvisioningService.reconnectForTenant(new TenantId(tenantId.strip()));
            if (qr.isEmpty()) {
                exchange.getIn()
                        .setBody(
                                new IngestErrorResponse(
                                        "Não foi possível obter o QR neste momento. Tente novamente ou verifique a instância na Evolution."));
                exchange.getMessage()
                        .setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }
            String plain = qr.get();
            String dataUri = plain.startsWith("data:image") ? plain : ("data:image/png;base64," + plain);
            String instanceId =
                    tenantConfigurationStore
                            .findByTenantId(new TenantId(tenantId.strip()))
                            .map(cfg -> cfg.whatsappInstanceId())
                            .filter(id -> id != null && !id.isBlank())
                            .orElse("");
            exchange.getMessage()
                    .setBody(new EvolutionPairingHttpResponse(tenantId.strip(), instanceId, plain, dataUri));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalStateException | IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        }
    }

    private static String parseQueryParam(String query, String name) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) {
                continue;
            }
            String k = URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8);
            if (name.equals(k)) {
                return URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** Resposta Camel / JSON público ao portal (sem segredos). */
    public record EvolutionPairingHttpResponse(
            String tenantId, String evolutionInstanceId, String qrcodePlainBase64, String qrcodeDataUri) {}
}
