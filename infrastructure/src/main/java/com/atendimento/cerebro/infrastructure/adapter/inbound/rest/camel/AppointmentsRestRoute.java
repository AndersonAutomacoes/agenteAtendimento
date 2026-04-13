package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/v1/appointments} — lista agendamentos do tenant autenticado.
 * {@code GET /api/v1/appointments/upcoming-count} — contagem nos próximos N dias.
 * {@code GET /api/v1/appointments/lookup-upcoming} — próximo agendamento por telefone(s).
 */
@Component
@Order(148)
public class AppointmentsRestRoute extends RouteBuilder {

    private final TenantAppointmentQueryPort appointmentQuery;
    private final CerebroGoogleCalendarProperties calendarProperties;

    public AppointmentsRestRoute(
            TenantAppointmentQueryPort appointmentQuery, CerebroGoogleCalendarProperties calendarProperties) {
        this.appointmentQuery = appointmentQuery;
        this.calendarProperties = calendarProperties;
    }

    @Override
    public void configure() {
        rest("/v1/appointments")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:appointmentsList")
                .get("/upcoming-count")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:appointmentsUpcomingCount")
                .get("/lookup-upcoming")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:appointmentsLookup");

        from("direct:appointmentsList")
                .routeId("appointmentsList")
                .process(this::handleList);

        from("direct:appointmentsUpcomingCount")
                .routeId("appointmentsUpcomingCount")
                .process(this::handleUpcomingCount);

        from("direct:appointmentsLookup")
                .routeId("appointmentsLookup")
                .process(this::handleLookup);
    }

    private void handleList(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        String scopeRaw = parseQueryParam(query, "scope");
        TenantAppointmentQueryPort.ListScope scope = parseScope(scopeRaw);
        String zone = calendarProperties.getZone();
        List<TenantAppointmentListItem> rows =
                appointmentQuery.list(new TenantId(tenantId), scope, zone);
        List<AppointmentHttpItem> items = rows.stream().map(r -> toHttpItem(r, zone)).toList();
        exchange.getMessage().setBody(new AppointmentsListResponse(items));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleUpcomingCount(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        int days = parsePositiveInt(parseQueryParam(query, "days"), 7);
        Instant from = Instant.now();
        Instant to = from.plus(days, ChronoUnit.DAYS);
        long count = appointmentQuery.countStartsInRange(new TenantId(tenantId), from, to);
        exchange.getMessage().setBody(new UpcomingCountResponse(count, days));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleLookup(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        String phonesRaw = parseQueryParam(query, "phones");
        if (phonesRaw == null || phonesRaw.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("phones é obrigatório (lista separada por vírgulas)"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        List<String> digits = new ArrayList<>();
        for (String p : phonesRaw.split(",")) {
            String d = p.replaceAll("\\D", "");
            if (!d.isEmpty()) {
                digits.add(d);
            }
        }
        if (digits.isEmpty()) {
            exchange.getMessage().setBody(new AppointmentLookupResponse(Map.of()));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
            return;
        }
        String zone = calendarProperties.getZone();
        Map<String, TenantAppointmentListItem> found =
                appointmentQuery.findEarliestUpcomingByPhoneDigits(new TenantId(tenantId), digits, zone);
        Map<String, AppointmentLookupEntry> out = new LinkedHashMap<>();
        for (String d : digits) {
            TenantAppointmentListItem row = found.get(d);
            if (row != null) {
                out.put(d, new AppointmentLookupEntry(row.startsAt().toString(), row.clientName(), row.serviceName()));
            }
        }
        exchange.getMessage().setBody(new AppointmentLookupResponse(out));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private static AppointmentHttpItem toHttpItem(TenantAppointmentListItem r, String zone) {
        String statusLabel = statusLabel(r, zone);
        boolean todayHighlight =
                r.status() == TenantAppointmentListItem.AppointmentStatus.TODAY;
        return new AppointmentHttpItem(
                r.id(),
                r.startsAt().toString(),
                r.endsAt().toString(),
                r.clientName(),
                r.serviceName(),
                r.conversationId(),
                statusLabel,
                todayHighlight,
                r.status().name());
    }

    private static String statusLabel(TenantAppointmentListItem r, String zone) {
        java.time.ZoneId z = java.time.ZoneId.of(zone);
        java.time.LocalDate today = java.time.LocalDate.now(z);
        java.time.LocalDate day = r.startsAt().atZone(z).toLocalDate();
        java.time.Instant now = java.time.Instant.now();
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

    private static TenantAppointmentQueryPort.ListScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return TenantAppointmentQueryPort.ListScope.ALL;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "today" -> TenantAppointmentQueryPort.ListScope.TODAY;
            case "future" -> TenantAppointmentQueryPort.ListScope.FUTURE;
            default -> TenantAppointmentQueryPort.ListScope.ALL;
        };
    }

    private static int parsePositiveInt(String raw, int defaultVal) {
        if (raw == null || raw.isBlank()) {
            return defaultVal;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : defaultVal;
        } catch (NumberFormatException e) {
            return defaultVal;
        }
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

    public record AppointmentsListResponse(List<AppointmentHttpItem> appointments) {}

    public record AppointmentHttpItem(
            long id,
            String startsAt,
            String endsAt,
            String clientName,
            String serviceName,
            String conversationId,
            String statusLabel,
            boolean todayHighlight,
            String status) {}

    public record UpcomingCountResponse(long count, int days) {}

    public record AppointmentLookupEntry(String startsAt, String clientName, String serviceName) {}

    public record AppointmentLookupResponse(Map<String, AppointmentLookupEntry> byPhoneDigits) {}
}
