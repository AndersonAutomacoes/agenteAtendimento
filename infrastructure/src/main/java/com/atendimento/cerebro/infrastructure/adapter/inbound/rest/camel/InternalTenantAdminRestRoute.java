package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.service.TenantInviteService;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Backoffice interno (operador AxeZap): cria tenant + plano + convite.
 */
@Component
@Order(102)
public class InternalTenantAdminRestRoute extends RouteBuilder {

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final TenantInviteService tenantInviteService;
    private final JdbcClient jdbcClient;

    public InternalTenantAdminRestRoute(
            TenantConfigurationStorePort tenantConfigurationStore,
            TenantInviteService tenantInviteService,
            JdbcClient jdbcClient) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.tenantInviteService = tenantInviteService;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void configure() {
        onException(IllegalStateException.class)
                .onWhen(simple("${routeId} regex '^internalTenant.*'"))
                .handled(true)
                .process(
                        exchange -> {
                            Throwable cause =
                                    exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            String message =
                                    cause != null && cause.getMessage() != null
                                            ? cause.getMessage()
                                            : "Serviço temporariamente indisponível.";
                            exchange.getMessage().setBody(new IngestErrorResponse(message));
                            exchange.getMessage()
                                    .setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                            exchange.getMessage()
                                    .setHeader(
                                            Exchange.HTTP_RESPONSE_CODE,
                                            HttpStatus.SERVICE_UNAVAILABLE.value());
                        });

        rest("/v1/internal/tenants")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(InternalTenantListResponse.class)
                .to("direct:internalTenantList")
                .post()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(CreateInternalTenantRequest.class)
                .outType(CreateInternalTenantResponse.class)
                .to("direct:internalTenantCreate");

        rest("/v1/internal/tenants/{tenantId}")
                .put()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(UpdateInternalTenantRequest.class)
                .outType(UpdateInternalTenantResponse.class)
                .to("direct:internalTenantUpdate")
                .delete()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(UpdateInternalTenantResponse.class)
                .to("direct:internalTenantDeactivate");

        rest("/v1/internal/tenants/{tenantId}/invites")
                .post()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(CreateTenantInviteRequest.class)
                .outType(CreateTenantInviteResponse.class)
                .to("direct:internalTenantInvite");

        rest("/v1/internal/tenants/{tenantId}/activate")
                .post()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(UpdateInternalTenantResponse.class)
                .to("direct:internalTenantActivate");

        from("direct:internalTenantList")
                .routeId("internalTenantList")
                .process(this::handleList);

        from("direct:internalTenantCreate")
                .routeId("internalTenantCreate")
                .process(this::handleCreate);

        from("direct:internalTenantUpdate")
                .routeId("internalTenantUpdate")
                .process(this::handleUpdate);

        from("direct:internalTenantDeactivate")
                .routeId("internalTenantDeactivate")
                .process(this::handleDeactivate);

        from("direct:internalTenantActivate")
                .routeId("internalTenantActivate")
                .process(this::handleActivate);

        from("direct:internalTenantInvite")
                .routeId("internalTenantInvite")
                .process(this::handleTenantInvite);
    }

    private void handleList(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        String q = stripToNull(parseQueryParam(query, "q"));
        List<InternalTenantRow> rows;
        if (q == null) {
            rows =
                    jdbcClient
                            .sql(
                                    """
                                    SELECT c.tenant_id,
                                           c.establishment_name,
                                           coalesce(s.active, true) AS active,
                                           coalesce(
                                               (
                                                   SELECT pu.profile_level
                                                   FROM portal_user pu
                                                   WHERE pu.tenant_id = c.tenant_id
                                                   ORDER BY pu.updated_at DESC NULLS LAST, pu.created_at DESC
                                                   LIMIT 1
                                               ),
                                               c.profile_level
                                           ) AS profile_level,
                                           c.business_contacts,
                                           c.billing_compliant,
                                           (
                                               SELECT count(*)
                                               FROM tenant_appointments a
                                               WHERE a.tenant_id = c.tenant_id
                                                 AND a.starts_at >= date_trunc('month', now())
                                                 AND a.starts_at < date_trunc('month', now()) + interval '1 month'
                                                 AND a.booking_status = 'AGENDADO'
                                           ) AS monthly_appointments_used,
                                           (
                                               SELECT l.max_appointments_per_month
                                               FROM tenant_plan_limit l
                                               WHERE l.profile_level = c.profile_level
                                           ) AS monthly_appointments_limit
                                    FROM tenant_configuration c
                                    LEFT JOIN internal_tenant_status s
                                      ON s.tenant_id = c.tenant_id
                                    ORDER BY tenant_id
                                    LIMIT 300
                                    """)
                            .query(
                                    (rs, i) ->
                                            new InternalTenantRow(
                                                    rs.getString("tenant_id"),
                                                    rs.getString("establishment_name"),
                                                    rs.getBoolean("active"),
                                                    rs.getString("profile_level"),
                                                    rs.getString("business_contacts"),
                                                    rs.getBoolean("billing_compliant"),
                                                    rs.getInt("monthly_appointments_used"),
                                                    (Integer)
                                                            rs.getObject(
                                                                    "monthly_appointments_limit",
                                                                    Integer.class)))
                            .list();
        } else {
            String like = "%" + q.toLowerCase() + "%";
            rows =
                    jdbcClient
                            .sql(
                                    """
                                    SELECT c.tenant_id,
                                           c.establishment_name,
                                           coalesce(s.active, true) AS active,
                                           coalesce(
                                               (
                                                   SELECT pu.profile_level
                                                   FROM portal_user pu
                                                   WHERE pu.tenant_id = c.tenant_id
                                                   ORDER BY pu.updated_at DESC NULLS LAST, pu.created_at DESC
                                                   LIMIT 1
                                               ),
                                               c.profile_level
                                           ) AS profile_level,
                                           c.business_contacts,
                                           c.billing_compliant,
                                           (
                                               SELECT count(*)
                                               FROM tenant_appointments a
                                               WHERE a.tenant_id = c.tenant_id
                                                 AND a.starts_at >= date_trunc('month', now())
                                                 AND a.starts_at < date_trunc('month', now()) + interval '1 month'
                                                 AND a.booking_status = 'AGENDADO'
                                           ) AS monthly_appointments_used,
                                           (
                                               SELECT l.max_appointments_per_month
                                               FROM tenant_plan_limit l
                                               WHERE l.profile_level = c.profile_level
                                           ) AS monthly_appointments_limit
                                    FROM tenant_configuration c
                                    LEFT JOIN internal_tenant_status s
                                      ON s.tenant_id = c.tenant_id
                                    WHERE lower(c.tenant_id) LIKE ?
                                       OR lower(coalesce(c.establishment_name, '')) LIKE ?
                                       OR lower(coalesce(c.business_contacts, '')) LIKE ?
                                    ORDER BY tenant_id
                                    LIMIT 300
                                    """)
                            .param(like)
                            .param(like)
                            .param(like)
                            .query(
                                    (rs, i) ->
                                            new InternalTenantRow(
                                                    rs.getString("tenant_id"),
                                                    rs.getString("establishment_name"),
                                                    rs.getBoolean("active"),
                                                    rs.getString("profile_level"),
                                                    rs.getString("business_contacts"),
                                                    rs.getBoolean("billing_compliant"),
                                                    rs.getInt("monthly_appointments_used"),
                                                    (Integer)
                                                            rs.getObject(
                                                                    "monthly_appointments_limit",
                                                                    Integer.class)))
                            .list();
        }
        exchange.getMessage().setBody(new InternalTenantListResponse(rows));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleCreate(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        CreateInternalTenantRequest body = exchange.getIn().getBody(CreateInternalTenantRequest.class);
        if (body == null || body.tenantId() == null || body.tenantId().isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            TenantId target = new TenantId(body.tenantId().strip());
            ProfileLevel level = parseProfile(body.profileLevel());
            TenantConfiguration base = tenantConfigurationStore.findByTenantId(target).orElseGet(() -> TenantConfiguration.defaults(target));
            String contacts = mergeContacts(stripToNull(body.customerEmail()), base.businessContacts());
            TenantConfiguration updated =
                    new TenantConfiguration(
                            target,
                            base.systemPrompt(),
                            base.whatsappProviderType(),
                            base.whatsappApiKey(),
                            base.whatsappInstanceId(),
                            base.whatsappBaseUrl(),
                            level,
                            base.portalPasswordHash(),
                            base.googleCalendarId(),
                            stripToNull(body.establishmentName()),
                            base.businessAddress(),
                            base.openingHours(),
                            contacts,
                            base.businessFacilities(),
                            base.defaultAppointmentMinutes(),
                            base.billingCompliant(),
                            base.calendarAccessNotes(),
                            base.spreadsheetUrl(),
                            base.whatsappBusinessNumber());
            tenantConfigurationStore.upsert(updated);
            String inviteEmail = stripToNull(body.customerEmail());
            String inviteCode = tenantInviteService.createInviteAndSendEmail(
                    target, 5, null, inviteEmail, stripToNull(body.establishmentName()));
            exchange.getMessage()
                    .setBody(
                            new CreateInternalTenantResponse(
                                    target.value(),
                                    level.name(),
                                    inviteCode,
                                    "Tenant criado/atualizado. Convite enviado para " + inviteEmail + "."));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        } catch (IllegalStateException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private void handleUpdate(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        String tenantPath = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantPath == null || tenantPath.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        UpdateInternalTenantRequest body = exchange.getIn().getBody(UpdateInternalTenantRequest.class);
        if (body == null) {
            exchange.getIn().setBody(new IngestErrorResponse("payload obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            TenantId target = new TenantId(tenantPath.strip());
            ProfileLevel level = parseProfile(body.profileLevel());
            TenantConfiguration base =
                    tenantConfigurationStore.findByTenantId(target).orElseGet(() -> TenantConfiguration.defaults(target));
            String contacts = mergeContacts(stripToNull(body.customerEmail()), base.businessContacts());
            TenantConfiguration updated =
                    new TenantConfiguration(
                            target,
                            base.systemPrompt(),
                            base.whatsappProviderType(),
                            base.whatsappApiKey(),
                            base.whatsappInstanceId(),
                            base.whatsappBaseUrl(),
                            level,
                            base.portalPasswordHash(),
                            base.googleCalendarId(),
                            preferred(body.establishmentName(), base.establishmentName()),
                            base.businessAddress(),
                            base.openingHours(),
                            contacts,
                            base.businessFacilities(),
                            base.defaultAppointmentMinutes(),
                            base.billingCompliant(),
                            base.calendarAccessNotes(),
                            base.spreadsheetUrl(),
                            base.whatsappBusinessNumber());
            tenantConfigurationStore.upsert(updated);
            if (body.active() != null) {
                jdbcClient
                        .sql(
                                """
                                INSERT INTO internal_tenant_status (tenant_id, active, updated_at)
                                VALUES (?, ?, now())
                                ON CONFLICT (tenant_id) DO UPDATE
                                   SET active = EXCLUDED.active,
                                       updated_at = now()
                                """)
                        .param(target.value())
                        .param(body.active())
                        .update();
            }
            jdbcClient
                    .sql(
                            """
                            UPDATE portal_user
                               SET profile_level = ?
                             WHERE tenant_id = ?
                               AND profile_level <> 'COMERCIAL'
                            """)
                    .param(level.name())
                    .param(target.value())
                    .update();
            exchange.getMessage()
                    .setBody(
                            new UpdateInternalTenantResponse(
                                    target.value(),
                                    true,
                                    "Tenant atualizado com sucesso."));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        } catch (IllegalStateException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private void handleDeactivate(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        String tenantPath = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantPath == null || tenantPath.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            TenantId target = new TenantId(tenantPath.strip());
            jdbcClient
                    .sql(
                            """
                            INSERT INTO internal_tenant_status (tenant_id, active, updated_at)
                            VALUES (?, false, now())
                            ON CONFLICT (tenant_id) DO UPDATE
                               SET active = false,
                                   updated_at = now()
                            """)
                    .param(target.value())
                    .update();
            exchange.getMessage()
                    .setBody(
                            new UpdateInternalTenantResponse(
                                    target.value(),
                                    false,
                                    "Tenant marcado como inativo."));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        } catch (IllegalStateException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private void handleActivate(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        String tenantPath = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantPath == null || tenantPath.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            TenantId target = new TenantId(tenantPath.strip());
            jdbcClient
                    .sql(
                            """
                            INSERT INTO internal_tenant_status (tenant_id, active, updated_at)
                            VALUES (?, true, now())
                            ON CONFLICT (tenant_id) DO UPDATE
                               SET active = true,
                                   updated_at = now()
                            """)
                    .param(target.value())
                    .update();
            exchange.getMessage()
                    .setBody(
                            new UpdateInternalTenantResponse(
                                    target.value(),
                                    true,
                                    "Tenant marcado como ativo."));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        } catch (IllegalStateException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private void handleTenantInvite(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        String tenantPath = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantPath == null || tenantPath.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        CreateTenantInviteRequest body = exchange.getIn().getBody(CreateTenantInviteRequest.class);
        int maxUses = body != null && body.maxUses() != null && body.maxUses() > 0 ? body.maxUses() : 5;
        try {
            TenantId tenantId = new TenantId(tenantPath.strip());
            TenantConfiguration cfg =
                    tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));
            String inviteEmail = resolveInviteEmail(body != null ? body.inviteEmail() : null, cfg.businessContacts());
            if (inviteEmail == null) {
                exchange.getIn().setBody(new IngestErrorResponse("inviteEmail é obrigatório quando o tenant não possui contacto de e-mail"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
                return;
            }
            String inviteCode = tenantInviteService.createInviteAndSendEmail(
                    tenantId, maxUses, null, inviteEmail, cfg.establishmentName());
            exchange.getMessage()
                    .setBody(
                            new CreateTenantInviteResponse(
                                    tenantId.value(),
                                    inviteCode,
                                    "Convite gerado e enviado para " + inviteEmail + "."));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
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
            String k = URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8);
            if (name.equals(k)) {
                return URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String mergeContacts(String email, String current) {
        if (email == null) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return "email: " + email;
        }
        String replaced = current.replaceAll("(?im)^\\s*email\\s*:.*(?:\\R|$)", "");
        String cleaned = replaced.strip();
        if (cleaned.isBlank()) {
            return "email: " + email;
        }
        return cleaned + "\nemail: " + email;
    }

    private static String preferred(String preferred, String fallback) {
        String value = stripToNull(preferred);
        return value != null ? value : fallback;
    }

    private static String stripToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String resolveInviteEmail(String inviteEmail, String contacts) {
        String direct = stripToNull(inviteEmail);
        if (direct != null) {
            return direct;
        }
        if (contacts == null || contacts.isBlank()) {
            return null;
        }
        String line = java.util.Arrays.stream(contacts.split("\\R"))
                .map(String::trim)
                .filter(x -> x.toLowerCase().startsWith("email:"))
                .findFirst()
                .orElse(null);
        if (line == null) {
            return null;
        }
        return stripToNull(line.substring(6));
    }

    private static ProfileLevel parseProfile(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProfileLevel.BASIC;
        }
        return ProfileLevel.valueOf(raw.strip().toUpperCase());
    }

    private static boolean isCommercialOperator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof PortalAuthenticationToken pat)) {
            return false;
        }
        return pat.getProfileLevel() == ProfileLevel.COMERCIAL;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateInternalTenantRequest(
            String tenantId,
            String establishmentName,
            String customerEmail,
            String profileLevel) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateInternalTenantResponse(
            String tenantId, String profileLevel, String inviteCode, String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateTenantInviteRequest(Integer maxUses, String inviteEmail) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateTenantInviteResponse(String tenantId, String inviteCode, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InternalTenantRow(
            String tenantId,
            String establishmentName,
            boolean active,
            String profileLevel,
            String contacts,
            boolean billingCompliant,
            int monthlyAppointmentsUsed,
            Integer monthlyAppointmentsLimit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateInternalTenantRequest(
            String establishmentName, String customerEmail, String profileLevel, Boolean active) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateInternalTenantResponse(String tenantId, boolean active, String message) {}

    public record InternalTenantListResponse(List<InternalTenantRow> tenants) {}
}
