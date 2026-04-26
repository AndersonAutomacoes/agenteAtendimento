package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.TenantServiceDto;
import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.TenantServicesStorePort;
import com.atendimento.cerebro.application.service.TenantInviteService;
import com.atendimento.cerebro.domain.portal.PortalUser;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.CreateInviteRequest;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.CreateInviteResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.PortalUserJson;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.PortalUsersResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.TenantServiceWriteItem;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.TenantServicesListResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.TenantOnboardingDtos.TenantServicesWriteRequest;
import java.util.ArrayList;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Complemento da configuração do tenant: utilizadores do portal, convites, catálogo de serviços.
 * {@code /api/v1/tenant/...} via servlet Camel.
 */
@Component
@Order(101)
public class TenantOnboardingRestRoute extends RouteBuilder {

    private final PortalUserStorePort portalUserStore;
    private final TenantInviteService tenantInviteService;
    private final TenantServicesStorePort tenantServicesStore;
    private final TenantConfigurationStorePort tenantConfigurationStore;

    public TenantOnboardingRestRoute(
            PortalUserStorePort portalUserStore,
            TenantInviteService tenantInviteService,
            TenantServicesStorePort tenantServicesStore,
            TenantConfigurationStorePort tenantConfigurationStore) {
        this.portalUserStore = portalUserStore;
        this.tenantInviteService = tenantInviteService;
        this.tenantServicesStore = tenantServicesStore;
        this.tenantConfigurationStore = tenantConfigurationStore;
    }

    @Override
    public void configure() {
        rest("/v1/tenant/portal-users")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(PortalUsersResponse.class)
                .to("direct:tenantPortalUsersGet");

        rest("/v1/tenant/invites")
                .post()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(CreateInviteRequest.class)
                .outType(CreateInviteResponse.class)
                .to("direct:tenantInvitesPost");

        rest("/v1/tenant/services")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(TenantServicesListResponse.class)
                .to("direct:tenantServicesGet")
                .put()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(TenantServicesWriteRequest.class)
                .to("direct:tenantServicesPut");

        from("direct:tenantPortalUsersGet")
                .routeId("tenantPortalUsersGet")
                .process(this::handlePortalUsersGet);

        from("direct:tenantInvitesPost")
                .routeId("tenantInvitesPost")
                .process(this::handleInvitesPost);

        from("direct:tenantServicesGet")
                .routeId("tenantServicesGet")
                .process(this::handleServicesGet);

        from("direct:tenantServicesPut")
                .routeId("tenantServicesPut")
                .process(this::handleServicesPut);
    }

    private void handlePortalUsersGet(Exchange exchange) {
        String q = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, q);
        if (tenantId == null) {
            return;
        }
        List<PortalUser> rows = portalUserStore.listByTenantId(new TenantId(tenantId));
        List<PortalUserJson> out = new ArrayList<>();
        for (PortalUser u : rows) {
            out.add(
                    new PortalUserJson(
                            u.id().toString(), u.firebaseUid(), u.profileLevel().name()));
        }
        exchange.getMessage().setBody(new PortalUsersResponse(out));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleInvitesPost(Exchange exchange) {
        CreateInviteRequest req = exchange.getIn().getBody(CreateInviteRequest.class);
        String requestedTenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requestedTenantId);
        if (tenantId == null) {
            return;
        }
        int max = req != null && req.maxUses() != null && req.maxUses() > 0 ? req.maxUses() : 5;
        var cfg = tenantConfigurationStore.findByTenantId(new TenantId(tenantId)).orElse(null);
        String inviteEmail = resolveInviteEmail(req != null ? req.inviteEmail() : null, cfg != null ? cfg.businessContacts() : null);
        if (inviteEmail == null) {
            exchange.getIn().setBody(new IngestErrorResponse("inviteEmail é obrigatório quando não há email de contacto no tenant"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        String code = tenantInviteService.createInviteAndSendEmail(
                new TenantId(tenantId),
                max,
                null,
                inviteEmail,
                cfg != null ? cfg.establishmentName() : null);
        exchange.getMessage()
                .setBody(
                        new CreateInviteResponse(
                                code,
                                "Convite gerado e enviado por e-mail com sucesso.",
                                "O e-mail do utilizador final não fica no AxeZap após o envio.",
                                inviteEmail));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleServicesGet(Exchange exchange) {
        String q = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, q);
        if (tenantId == null) {
            return;
        }
        var list = tenantServicesStore.listByTenant(new TenantId(tenantId));
        exchange.getMessage().setBody(new TenantServicesListResponse(list));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleServicesPut(Exchange exchange) {
        TenantServicesWriteRequest body = exchange.getIn().getBody(TenantServicesWriteRequest.class);
        if (body == null || body.services() == null) {
            exchange.getIn().setBody(new IngestErrorResponse("services é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        String requestedTenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        String tid = CamelAuthSupport.authorizedTenantOrAbort(exchange, requestedTenantId);
        if (tid == null) {
            return;
        }
        try {
            List<TenantServiceDto> acc = new ArrayList<>();
            for (TenantServiceWriteItem it : body.services()) {
                if (it.name() == null || it.name().isBlank()) {
                    continue;
                }
                boolean active = it.active() == null || it.active();
                acc.add(
                        new TenantServiceDto(
                                null,
                                it.name().strip(),
                                it.durationMinutes(),
                                active));
            }
            tenantServicesStore.upsertAll(new TenantId(tid), acc);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
            exchange.getIn().setBody(null);
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
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
            var k = java.net.URLDecoder.decode(part.substring(0, i), java.nio.charset.StandardCharsets.UTF_8);
            if (name.equals(k) && i + 1 < part.length()) {
                return java.net.URLDecoder.decode(
                        part.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String resolveInviteEmail(String inviteEmail, String contacts) {
        if (inviteEmail != null && !inviteEmail.isBlank()) {
            return inviteEmail.strip();
        }
        if (contacts == null || contacts.isBlank()) {
            return null;
        }
        for (String line : contacts.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("email:")) {
                String extracted = trimmed.substring(6).trim();
                return extracted.isBlank() ? null : extracted;
            }
        }
        return null;
    }
}
