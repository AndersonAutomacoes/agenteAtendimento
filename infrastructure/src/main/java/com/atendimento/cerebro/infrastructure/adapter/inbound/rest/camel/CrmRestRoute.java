package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.crm.CrmSalesInsightFormatter;
import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/v1/crm/summary} — ficha agregada (CRM + mensagens WhatsApp + agendamentos).
 * {@code PATCH /api/v1/crm/customers/{customerId}/notes} — notas internas.
 * {@code GET /api/v1/crm/opportunities} — leads pendentes (PENDING_LEAD).
 * {@code POST /api/v1/crm/opportunities/{customerId}/assume} — assumir follow-up manual.
 */
@Component
@Order(149)
public class CrmRestRoute extends RouteBuilder {

    private static final int MESSAGE_LIMIT = 200;

    private final CrmCustomerQueryPort crmCustomerQuery;
    private final CrmCustomerStorePort crmCustomerStore;
    private final ChatMessageRepository chatMessageRepository;
    private final TenantAppointmentQueryPort appointmentQuery;
    private final CerebroGoogleCalendarProperties calendarProperties;

    public CrmRestRoute(
            CrmCustomerQueryPort crmCustomerQuery,
            CrmCustomerStorePort crmCustomerStore,
            ChatMessageRepository chatMessageRepository,
            TenantAppointmentQueryPort appointmentQuery,
            CerebroGoogleCalendarProperties calendarProperties) {
        this.crmCustomerQuery = crmCustomerQuery;
        this.crmCustomerStore = crmCustomerStore;
        this.chatMessageRepository = chatMessageRepository;
        this.appointmentQuery = appointmentQuery;
        this.calendarProperties = calendarProperties;
    }

    @Override
    public void configure() {
        rest("/v1/crm")
                .get("/summary")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:crmSummary")
                .patch("/customers/{customerId}/notes")
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(CrmNotesPatchBody.class)
                .to("direct:crmPatchNotes")
                .get("/opportunities")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:crmOpportunities")
                .post("/opportunities/{customerId}/assume")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:crmAssumeOpportunity");

        from("direct:crmSummary").routeId("crmSummary").process(this::handleSummary);

        from("direct:crmPatchNotes").routeId("crmPatchNotes").process(this::handlePatchNotes);

        from("direct:crmOpportunities").routeId("crmOpportunities").process(this::handleOpportunities);

        from("direct:crmAssumeOpportunity").routeId("crmAssumeOpportunity").process(this::handleAssumeOpportunity);
    }

    private void handleSummary(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        String conversationId = parseQueryParam(query, "conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("conversationId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        conversationId = conversationId.strip();
        TenantId tenant = new TenantId(tenantId);
        String zone = calendarProperties.getZone();

        Optional<CrmCustomerRecord> crm = crmCustomerQuery.findByTenantAndConversationId(tenant, conversationId);

        List<CrmMessageHttp> messageItems = new ArrayList<>();
        Optional<String> digitsOpt = CrmConversationSupport.phoneDigitsOnlyFromConversationId(conversationId);
        if (digitsOpt.isPresent()) {
            String digits = digitsOpt.get();
            List<ChatMessage> msgs =
                    chatMessageRepository.findRecentMessagesChronological(
                            tenant, digits, Instant.EPOCH, Instant.now(), MESSAGE_LIMIT);
            for (ChatMessage m : msgs) {
                long id = m.id() != null ? m.id() : 0L;
                messageItems.add(
                        new CrmMessageHttp(
                                id,
                                m.role().name(),
                                m.content(),
                                DateTimeFormatter.ISO_INSTANT.format(m.timestamp())));
            }
        }

        List<TenantAppointmentListItem> appts =
                appointmentQuery.listByConversationId(tenant, conversationId, zone);
        List<CrmAppointmentHttp> apptHttp = appts.stream().map(a -> toAppointmentHttp(a, zone)).toList();

        CrmCustomerHttp customerHttp =
                crm.map(c -> toCustomerHttp(c, CrmSalesInsightFormatter.buildInsight(c))).orElse(null);
        exchange.getMessage()
                .setBody(new CrmSummaryResponse(customerHttp, List.copyOf(messageItems), apptHttp));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handlePatchNotes(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String idRaw = exchange.getMessage().getHeader("customerId", String.class);
        if (idRaw == null || idRaw.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("customerId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        UUID customerId;
        try {
            customerId = UUID.fromString(idRaw.strip());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse("customerId inválido"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        CrmNotesPatchBody body = exchange.getIn().getBody(CrmNotesPatchBody.class);
        String notes = body != null && body.internalNotes() != null ? body.internalNotes() : "";
        crmCustomerStore.updateInternalNotes(new TenantId(tenantId), customerId, notes);
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private void handleOpportunities(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        TenantId tenant = new TenantId(tenantId);
        var rows = crmCustomerQuery.listPendingLeadOpportunities(tenant);
        List<CrmCustomerHttp> items =
                rows.stream()
                        .map(c -> toCustomerHttp(c, CrmSalesInsightFormatter.buildInsight(c)))
                        .toList();
        exchange.getMessage().setBody(new CrmOpportunitiesResponse(items));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleAssumeOpportunity(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String idRaw = exchange.getMessage().getHeader("customerId", String.class);
        if (idRaw == null || idRaw.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("customerId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        UUID customerId;
        try {
            customerId = UUID.fromString(idRaw.strip());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse("customerId inválido"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        boolean ok = crmCustomerStore.assumePendingLead(new TenantId(tenantId), customerId);
        if (!ok) {
            exchange.getIn().setBody(new IngestErrorResponse("Oportunidade não encontrada ou já tratada"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NOT_FOUND.value());
            return;
        }
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private CrmCustomerHttp toCustomerHttp(CrmCustomerRecord c, String aiSalesInsight) {
        String lastIntentAt =
                c.lastIntentAt() != null ? DateTimeFormatter.ISO_INSTANT.format(c.lastIntentAt()) : null;
        String lastDetected =
                c.lastDetectedIntent() != null && !c.lastDetectedIntent().isBlank()
                        ? c.lastDetectedIntent()
                        : c.lastIntent();
        return new CrmCustomerHttp(
                c.id().toString(),
                c.tenantId(),
                c.conversationId(),
                c.phoneNumber(),
                c.fullName(),
                c.email(),
                DateTimeFormatter.ISO_INSTANT.format(c.firstInteraction()),
                c.totalAppointments(),
                c.internalNotes(),
                c.lastIntent(),
                lastDetected,
                c.leadScore(),
                c.isConverted(),
                c.intentStatus(),
                lastIntentAt,
                aiSalesInsight);
    }

    private static CrmAppointmentHttp toAppointmentHttp(TenantAppointmentListItem r, String zoneId) {
        ZoneId z = ZoneId.of(zoneId);
        String statusLabel = statusLabel(r, z);
        return new CrmAppointmentHttp(
                r.id(),
                r.startsAt().toString(),
                r.endsAt().toString(),
                r.clientName(),
                r.serviceName(),
                statusLabel,
                r.status().name());
    }

    private static String statusLabel(TenantAppointmentListItem r, ZoneId z) {
        LocalDate today = LocalDate.now(z);
        LocalDate day = r.startsAt().atZone(z).toLocalDate();
        Instant now = Instant.now();
        if (day.equals(today)) {
            if (r.startsAt().isBefore(now)) {
                return "Concluído";
            }
            return "Hoje";
        }
        if (r.startsAt().isBefore(now)) {
            return "Concluído";
        }
        return "Agendado";
    }

    private static String parseRequestedTenantId(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        return tenantId == null || tenantId.isBlank() ? null : tenantId.strip();
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

    public record CrmSummaryResponse(
            CrmCustomerHttp customer, List<CrmMessageHttp> messages, List<CrmAppointmentHttp> appointments) {}

    public record CrmCustomerHttp(
            String id,
            String tenantId,
            String conversationId,
            String phoneNumber,
            String fullName,
            String email,
            String firstInteraction,
            int totalAppointments,
            String internalNotes,
            String lastIntent,
            String lastDetectedIntent,
            int leadScore,
            boolean isConverted,
            String intentStatus,
            String lastIntentAt,
            String aiSalesInsight) {}

    public record CrmOpportunitiesResponse(List<CrmCustomerHttp> opportunities) {}

    public record CrmMessageHttp(long id, String role, String content, String occurredAt) {}

    public record CrmAppointmentHttp(
            long id,
            String startsAt,
            String endsAt,
            String clientName,
            String serviceName,
            String statusLabel,
            String status) {}

    public record CrmNotesPatchBody(String internalNotes) {}
}
