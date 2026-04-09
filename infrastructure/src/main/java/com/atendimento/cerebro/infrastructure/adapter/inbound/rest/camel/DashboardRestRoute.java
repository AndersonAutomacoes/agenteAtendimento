package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.DashboardRange;
import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.application.port.out.DashboardSummaryPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** {@code GET /api/v1/dashboard/summary?tenantId=&range=day|week|month} */
@Component
@Order(155)
public class DashboardRestRoute extends RouteBuilder {

    private final DashboardSummaryPort dashboardSummaryPort;

    public DashboardRestRoute(DashboardSummaryPort dashboardSummaryPort) {
        this.dashboardSummaryPort = dashboardSummaryPort;
    }

    @Override
    public void configure() {
        rest("/v1/dashboard")
                .get("/summary")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:dashboardSummaryGet");

        from("direct:dashboardSummaryGet")
                .routeId("dashboardSummaryGet")
                .process(this::handleGet);
    }

    private void handleGet(Exchange exchange) {
        String tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        tenantId = tenantId.strip();
        String rangeRaw = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "range");
        DashboardRange range = parseRange(rangeRaw);

        DashboardSummary summary = dashboardSummaryPort.load(new TenantId(tenantId), range);
        exchange.getMessage().setBody(summary);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private static DashboardRange parseRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return DashboardRange.day;
        }
        try {
            return DashboardRange.valueOf(raw.strip().toLowerCase());
        } catch (IllegalArgumentException e) {
            return DashboardRange.day;
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
