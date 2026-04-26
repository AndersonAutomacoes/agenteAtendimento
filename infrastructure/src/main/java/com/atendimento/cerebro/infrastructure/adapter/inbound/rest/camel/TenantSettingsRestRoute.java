package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.TenantSettingsUpdateCommand;
import com.atendimento.cerebro.application.port.in.UpdateTenantSettingsUseCase;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code GET/PUT /api/v1/tenant/settings} (servlet Camel {@code /api/*} + {@code /v1/tenant/settings}).
 */
@Component
@Order(100)
public class TenantSettingsRestRoute extends RouteBuilder {

    private final UpdateTenantSettingsUseCase updateTenantSettings;
    private final TenantConfigurationStorePort tenantConfigurationStore;

    public TenantSettingsRestRoute(
            UpdateTenantSettingsUseCase updateTenantSettings,
            TenantConfigurationStorePort tenantConfigurationStore) {
        this.updateTenantSettings = updateTenantSettings;
        this.tenantConfigurationStore = tenantConfigurationStore;
    }

    @Override
    public void configure() {
        rest("/v1/tenant/settings")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(TenantSettingsResponse.class)
                .to("direct:tenantSettingsGet")
                .put()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .type(TenantSettingsHttpRequest.class)
                .to("direct:tenantSettings");

        from("direct:tenantSettingsGet")
                .routeId("tenantSettingsGet")
                .process(this::handleGet);

        from("direct:tenantSettings")
                .routeId("tenantSettingsPut")
                .process(this::handlePut);
    }

    private void handleGet(Exchange exchange) {
        String requestedTenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            requestedTenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requestedTenantId);
        if (tenantId == null) {
            return;
        }
        TenantId tid = new TenantId(tenantId);
        TenantConfiguration c =
                tenantConfigurationStore.findByTenantId(tid).orElseGet(() -> TenantConfiguration.defaults(tid));
        TenantSettingsResponse body = new TenantSettingsResponse(
                c.tenantId().value(),
                c.profileLevel().name(),
                c.systemPrompt(),
                c.whatsappProviderType().name(),
                c.whatsappApiKey(),
                c.whatsappInstanceId(),
                c.whatsappBaseUrl(),
                c.googleCalendarId(),
                c.establishmentName(),
                c.businessAddress(),
                c.openingHours(),
                c.businessContacts(),
                c.businessFacilities(),
                c.defaultAppointmentMinutes(),
                c.billingCompliant(),
                c.calendarAccessNotes(),
                c.spreadsheetUrl(),
                c.whatsappBusinessNumber());
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    /** Extrai um parâmetro da query string {@code application/x-www-form-urlencoded}. */
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

    private void handlePut(Exchange exchange) {
        TenantSettingsHttpRequest body = exchange.getIn().getBody(TenantSettingsHttpRequest.class);
        String requestedTenantId = body != null ? body.tenantId() : null;
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requestedTenantId);
        if (tenantId == null) {
            return;
        }
        if (body.systemPrompt() == null) {
            exchange.getIn().setBody(new IngestErrorResponse("systemPrompt é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        WhatsAppProviderType providerType = null;
        if (body.whatsappProviderType() != null && !body.whatsappProviderType().isBlank()) {
            try {
                providerType = WhatsAppProviderType.valueOf(body.whatsappProviderType().strip());
            } catch (IllegalArgumentException e) {
                exchange.getIn().setBody(new IngestErrorResponse("whatsappProviderType inválido"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
                return;
            }
        }
        try {
            TenantSettingsUpdateCommand command = new TenantSettingsUpdateCommand(
                    body.systemPrompt(),
                    providerType,
                    body.whatsappApiKey(),
                    body.whatsappInstanceId(),
                    body.whatsappBaseUrl(),
                    body.googleCalendarId(),
                    body.establishmentName(),
                    body.businessAddress(),
                    body.openingHours(),
                    body.businessContacts(),
                    body.businessFacilities(),
                    body.defaultAppointmentMinutes(),
                    body.billingCompliant(),
                    body.calendarAccessNotes(),
                    body.spreadsheetUrl(),
                    body.whatsappBusinessNumber());
            updateTenantSettings.updateTenantSettings(new TenantId(tenantId), command);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
            exchange.getIn().setBody(null);
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        }
    }
}
