package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.apache.camel.Exchange;

final class AnalyticsPeriodQueryParser {

    private AnalyticsPeriodQueryParser() {}

    /**
     * Se ambos presentes, devolve período validado; se ambos ausentes, {@code null}; se só um presente, lança
     * ilegal.
     */
    static AnalyticsPeriodQuery parse(String httpQuery) {
        String startRaw = parseQueryParam(httpQuery, "startDate");
        String endRaw = parseQueryParam(httpQuery, "endDate");
        if ((startRaw == null || startRaw.isBlank()) && (endRaw == null || endRaw.isBlank())) {
            return null;
        }
        if (startRaw == null || startRaw.isBlank() || endRaw == null || endRaw.isBlank()) {
            throw new IllegalArgumentException("startDate e endDate são obrigatórios em conjunto");
        }
        Instant start;
        Instant end;
        try {
            start = Instant.parse(startRaw.strip());
            end = Instant.parse(endRaw.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("startDate e endDate devem ser ISO-8601 instant (ex.: 2026-04-01T00:00:00Z)");
        }
        AnalyticsPeriodQuery q = new AnalyticsPeriodQuery(start, end);
        q.validateOrThrow();
        return q;
    }

    static String parseQueryParam(String query, String name) {
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

    static String httpQuery(Exchange exchange) {
        return exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class);
    }

    /** {@code csv} ou {@code pdf}; inválido ou ausente → {@code null}. */
    static String parseFormat(String httpQuery) {
        String f = parseQueryParam(httpQuery, "format");
        if (f == null || f.isBlank()) {
            return null;
        }
        return switch (f.strip().toLowerCase()) {
            case "csv", "pdf" -> f.strip().toLowerCase();
            default -> null;
        };
    }
}
