package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.HourlyMessageVolumePoint;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCount;
import com.atendimento.cerebro.application.analytics.SentimentCount;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.application.port.out.AnalyticsQueryPort;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/v1/analytics/summary?tenantId=...} — métricas últimas 24h.
 * {@code GET /api/v1/analytics/hourly-messages?tenantId=...&hours=24} — volume por hora UTC.
 * {@code GET /api/v1/analytics/intents?tenantId=...&days=30} — contagem por intenção principal (pizza).
 * {@code GET /api/v1/analytics/stats?tenantId=...} — contagens em {@code chat_analytics} (intenção + sentimento).
 */
@Component
@Order(145)
public class AnalyticsRestRoute extends RouteBuilder {

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
    private static final int DEFAULT_HOURS = 24;
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 168;
    private static final int DEFAULT_INTENT_DAYS = 30;
    private static final int MIN_INTENT_DAYS = 1;
    private static final int MAX_INTENT_DAYS = 366;

    private final AnalyticsQueryPort analyticsQueryPort;
    private final AnalyticsIntentsRepository analyticsIntentsRepository;
    private final ChatAnalyticsRepository chatAnalyticsRepository;

    public AnalyticsRestRoute(
            AnalyticsQueryPort analyticsQueryPort,
            AnalyticsIntentsRepository analyticsIntentsRepository,
            ChatAnalyticsRepository chatAnalyticsRepository) {
        this.analyticsQueryPort = analyticsQueryPort;
        this.analyticsIntentsRepository = analyticsIntentsRepository;
        this.chatAnalyticsRepository = chatAnalyticsRepository;
    }

    @Override
    public void configure() {
        rest("/v1/analytics")
                .get("/summary")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:analyticsSummary")
                .get("/hourly-messages")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:analyticsHourlyMessages")
                .get("/intents")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:analyticsIntents")
                .get("/stats")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:analyticsChatStats");

        from("direct:analyticsSummary")
                .routeId("analyticsSummary")
                .process(this::handleSummary);

        from("direct:analyticsHourlyMessages")
                .routeId("analyticsHourlyMessages")
                .process(this::handleHourly);

        from("direct:analyticsIntents")
                .routeId("analyticsIntents")
                .process(this::handleIntents);

        from("direct:analyticsChatStats")
                .routeId("analyticsChatStats")
                .process(this::handleChatStats);
    }

    private void handleSummary(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        var summary = analyticsQueryPort.summaryLast24Hours(new TenantId(tenantId));
        var body =
                new AnalyticsSummaryHttpResponse(
                        summary.tenantId().value(),
                        ISO_INSTANT.format(summary.periodStart()),
                        ISO_INSTANT.format(summary.periodEnd()),
                        summary.totalMessages(),
                        summary.uniqueUsers(),
                        summary.successRatePercent());
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleHourly(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        int hours = parseHoursOrDefault(query);
        if (hours < MIN_HOURS || hours > MAX_HOURS) {
            exchange.getIn()
                    .setBody(
                            new IngestErrorResponse(
                                    "hours deve estar entre " + MIN_HOURS + " e " + MAX_HOURS));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        List<HourlyMessageVolumePoint> points = analyticsQueryPort.hourlyMessageVolume(new TenantId(tenantId), hours);
        InstantRange range = requestWindowForLastHours(hours);
        var body =
                new AnalyticsHourlyMessagesHttpResponse(
                        tenantId,
                        hours,
                        ISO_INSTANT.format(range.start()),
                        ISO_INSTANT.format(range.end()),
                        points.stream()
                                .map(
                                        p ->
                                                new HourlyPointHttpResponse(
                                                        ISO_INSTANT.format(p.bucketStartUtc()),
                                                        p.messageCount()))
                                .toList());
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleChatStats(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        AnalyticsPeriodQuery period;
        try {
            period = AnalyticsPeriodQueryParser.parse(query);
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        var agg =
                period == null
                        ? chatAnalyticsRepository.aggregateForTenant(new TenantId(tenantId))
                        : chatAnalyticsRepository.aggregateForTenant(
                                new TenantId(tenantId), period.start(), period.end());
        var intents =
                java.util.Arrays.stream(ChatMainIntent.values())
                        .map(
                                c ->
                                        new IntentCategoryCountHttp(
                                                c.dbValue(), agg.intentsByCategory().getOrDefault(c, 0L)))
                        .toList();
        var sentiments =
                java.util.Arrays.stream(ChatSentiment.values())
                        .map(
                                s ->
                                        new IntentCategoryCountHttp(
                                                s.dbValue(), agg.sentimentsByCategory().getOrDefault(s, 0L)))
                        .toList();
        var body =
                new ChatAnalyticsStatsHttpResponse(
                        tenantId, ISO_INSTANT.format(Instant.now()), intents, sentiments);
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleIntents(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
        AnalyticsPeriodQuery explicit;
        try {
            explicit = AnalyticsPeriodQueryParser.parse(query);
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse(e.getMessage()));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        TenantId tenant = new TenantId(tenantId);
        final int reportDays;
        final Instant periodStart;
        final Instant periodEnd;
        if (explicit != null) {
            periodStart = explicit.start();
            periodEnd = explicit.end();
            reportDays = (int) Math.max(1L, Duration.between(periodStart, periodEnd).toDays());
        } else {
            int days = parseDaysOrDefault(query);
            if (days < MIN_INTENT_DAYS || days > MAX_INTENT_DAYS) {
                exchange.getIn()
                        .setBody(
                                new IngestErrorResponse(
                                        "days deve estar entre " + MIN_INTENT_DAYS + " e " + MAX_INTENT_DAYS));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
                return;
            }
            periodEnd = Instant.now();
            periodStart = periodEnd.minus(days, ChronoUnit.DAYS);
            reportDays = days;
        }
        Duration windowLen = Duration.between(periodStart, periodEnd);
        Instant previousPeriodEnd = periodStart;
        Instant previousPeriodStart = periodStart.minus(windowLen);

        EnumMap<PrimaryIntentCategory, Long> perCat = newZeroIntentCounts();
        for (PrimaryIntentCount row :
                analyticsIntentsRepository.countByCategoryInRange(tenant, periodStart, periodEnd)) {
            perCat.merge(row.category(), row.count(), Long::sum);
        }
        List<IntentCategoryCountHttp> counts =
                java.util.Arrays.stream(PrimaryIntentCategory.values())
                        .map(c -> new IntentCategoryCountHttp(c.name(), perCat.get(c)))
                        .toList();

        EnumMap<PrimaryIntentCategory, Long> prevCat = newZeroIntentCounts();
        for (PrimaryIntentCount row :
                analyticsIntentsRepository.countByCategoryInRange(tenant, previousPeriodStart, previousPeriodEnd)) {
            prevCat.merge(row.category(), row.count(), Long::sum);
        }
        List<IntentCategoryCountHttp> previousCounts =
                java.util.Arrays.stream(PrimaryIntentCategory.values())
                        .map(c -> new IntentCategoryCountHttp(c.name(), prevCat.get(c)))
                        .toList();

        EnumMap<ConversationSentiment, Long> perSent = newZeroSentimentCounts();
        for (SentimentCount row :
                analyticsIntentsRepository.countBySentimentInRange(tenant, periodStart, periodEnd)) {
            perSent.merge(row.sentiment(), row.count(), Long::sum);
        }
        List<SentimentCountHttp> sentimentCounts =
                java.util.Arrays.stream(ConversationSentiment.values())
                        .map(s -> new SentimentCountHttp(s.name(), perSent.get(s)))
                        .toList();

        var body =
                new AnalyticsIntentsHttpResponse(
                        tenantId,
                        reportDays,
                        ISO_INSTANT.format(periodStart),
                        ISO_INSTANT.format(periodEnd),
                        counts,
                        previousCounts,
                        ISO_INSTANT.format(previousPeriodStart),
                        ISO_INSTANT.format(previousPeriodEnd),
                        sentimentCounts);
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private static EnumMap<PrimaryIntentCategory, Long> newZeroIntentCounts() {
        EnumMap<PrimaryIntentCategory, Long> m = new EnumMap<>(PrimaryIntentCategory.class);
        for (PrimaryIntentCategory c : PrimaryIntentCategory.values()) {
            m.put(c, 0L);
        }
        return m;
    }

    private static EnumMap<ConversationSentiment, Long> newZeroSentimentCounts() {
        EnumMap<ConversationSentiment, Long> m = new EnumMap<>(ConversationSentiment.class);
        for (ConversationSentiment s : ConversationSentiment.values()) {
            m.put(s, 0L);
        }
        return m;
    }

    private record InstantRange(Instant start, Instant end) {}

    /** Janela de filtro das consultas: [now − hours, now). */
    private static InstantRange requestWindowForLastHours(int hours) {
        Instant end = Instant.now();
        Instant start = end.minus(hours, ChronoUnit.HOURS);
        return new InstantRange(start, end);
    }

    private static String parseRequestedTenantId(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        return tenantId == null || tenantId.isBlank() ? null : tenantId.strip();
    }

    private static int parseHoursOrDefault(String query) {
        String raw = parseQueryParam(query, "hours");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_HOURS;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseDaysOrDefault(String query) {
        String raw = parseQueryParam(query, "days");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_INTENT_DAYS;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
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
}
