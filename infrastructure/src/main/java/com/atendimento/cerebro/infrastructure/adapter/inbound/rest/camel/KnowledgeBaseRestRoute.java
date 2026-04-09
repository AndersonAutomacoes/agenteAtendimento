package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /api/v1/knowledge-base?tenantId=} — arquivos indexados (pgvector).
 * {@code DELETE /api/v1/knowledge-base/{batchId}?tenantId=} — remove todos os vectores desse envio.
 */
@Component
@Order(140)
public class KnowledgeBaseRestRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBaseRestRoute.class);

    private final KnowledgeBasePort knowledgeBasePort;

    public KnowledgeBaseRestRoute(KnowledgeBasePort knowledgeBasePort) {
        this.knowledgeBasePort = knowledgeBasePort;
    }

    @Override
    public void configure() {
        rest("/v1/knowledge-base")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:knowledgeBaseList")
                .delete("/{batchId}")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:knowledgeBaseDelete");

        from("direct:knowledgeBaseList")
                .routeId("knowledgeBaseList")
                .process(this::handleList);

        from("direct:knowledgeBaseDelete")
                .routeId("knowledgeBaseDelete")
                .process(this::handleDelete);
    }

    private void handleList(Exchange exchange) {
        String tenantId = resolveTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            List<KnowledgeBaseFileHttpResponse> body =
                    knowledgeBasePort.listUploadedFiles(new TenantId(tenantId.strip())).stream()
                            .map(KnowledgeBaseFileHttpResponse::from)
                            .toList();
            exchange.getMessage().setBody(body);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (Exception e) {
            LOG.warn("knowledge-base list tenantId={}", tenantId, e);
            exchange.getIn().setBody(new IngestErrorResponse("Falha ao listar a base de conhecimento"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void handleDelete(Exchange exchange) {
        String tenantId = resolveTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        String batchId = exchange.getMessage().getHeader("batchId", String.class);
        if (batchId == null || batchId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("batchId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        int n = knowledgeBasePort.deleteByBatchId(new TenantId(tenantId.strip()), batchId.strip());
        if (n == 0) {
            exchange.getIn().setBody(new IngestErrorResponse("arquivo não encontrado para esta conta"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NOT_FOUND.value());
            return;
        }
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private static String resolveTenantId(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        return tenantId;
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
