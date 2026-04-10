package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.reports.AnalyticsReportExportService;
import com.atendimento.cerebro.infrastructure.reports.ReportI18n;
import com.lowagie.text.DocumentException;
import java.util.Objects;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** {@code GET /api/v1/analytics/export?tenantId=&startDate=&endDate=&locale=&format=csv|pdf} */
@Component
@Order(144)
public class AnalyticsExportRestRoute extends RouteBuilder {

    private final AnalyticsReportExportService exportService;

    public AnalyticsExportRestRoute(AnalyticsReportExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void configure() {
        rest("/v1/analytics")
                .get("/export")
                .bindingMode(RestBindingMode.off)
                .to("direct:analyticsExport");

        from("direct:analyticsExport").routeId("analyticsExport").process(this::handle);
    }

    private void handle(Exchange exchange) {
        String requested = parseRequestedTenantId(exchange);
        String tenantId = CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
        if (tenantId == null) {
            return;
        }
        String query = AnalyticsPeriodQueryParser.httpQuery(exchange);
        AnalyticsPeriodQuery period;
        try {
            period = AnalyticsPeriodQueryParser.parse(query);
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(jsonError(Objects.requireNonNullElse(e.getMessage(), "pedido inválido")));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        if (period == null) {
            exchange.getIn().setBody(jsonError("startDate e endDate são obrigatórios"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }

        String format = AnalyticsPeriodQueryParser.parseFormat(query);
        if (format == null) {
            exchange.getIn().setBody(jsonError("format deve ser csv ou pdf"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }

        String localeRaw = AnalyticsPeriodQueryParser.parseQueryParam(query, "locale");
        var locale = ReportI18n.parseLocaleParam(localeRaw);
        TenantId tenant = new TenantId(tenantId);

        try {
            byte[] bytes =
                    switch (format) {
                        case "csv" -> exportService.buildCsv(tenant, period.start(), period.end(), locale);
                        case "pdf" -> exportService.buildPdf(tenant, period.start(), period.end(), locale);
                        default -> throw new IllegalStateException();
                    };
            String ext = format;
            String filename =
                    "intelizap-report-" + tenantId.replaceAll("[^a-zA-Z0-9_-]", "_") + "." + ext;
            exchange.getMessage().setBody(bytes);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType(format));
            exchange.getMessage()
                    .setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (DocumentException e) {
            exchange.getIn().setBody(jsonError("Falha ao gerar PDF"));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private static String jsonError(String message) {
        String m =
                message.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", " ")
                        .replace("\n", " ");
        return "{\"error\":\"" + m + "\"}";
    }

    private static String contentType(String format) {
        return switch (format) {
            case "csv" -> "text/csv; charset=UTF-8";
            case "pdf" -> "application/pdf";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private static String parseRequestedTenantId(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = AnalyticsPeriodQueryParser.parseQueryParam(
                    exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        return tenantId == null || tenantId.isBlank() ? null : tenantId.strip();
    }
}
