package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code GET /api/v1/messages?tenantId=...} — histórico recente (monitorização WhatsApp).
 * {@code POST /api/v1/messages/{id}/retry?tenantId=...} — reenvio de uma resposta ASSISTANT em ERROR.
 */
@Component
@Order(150)
public class MessagesRestRoute extends RouteBuilder {

    private static final int DEFAULT_LIMIT = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final WhatsAppOutboundPort whatsAppOutboundPort;

    public MessagesRestRoute(ChatMessageRepository chatMessageRepository, WhatsAppOutboundPort whatsAppOutboundPort) {
        this.chatMessageRepository = chatMessageRepository;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
    }

    @Override
    public void configure() {
        rest("/v1/messages")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:messagesGet")
                .post("/{id}/retry")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:messagesRetry");

        from("direct:messagesGet")
                .routeId("messagesGet")
                .process(this::handleGet);

        from("direct:messagesRetry")
                .routeId("messagesRetry")
                .process(this::handleRetry);
    }

    private void handleGet(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        tenantId = tenantId.strip();
        List<ChatMessage> rows = chatMessageRepository.findLastByTenantId(new TenantId(tenantId), DEFAULT_LIMIT);
        List<ChatMessageItemResponse> body =
                rows.stream().map(MessagesRestRoute::toItem).toList();
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleRetry(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        tenantId = tenantId.strip();

        String idRaw = exchange.getMessage().getHeader("id", String.class);
        if (idRaw == null || idRaw.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("id é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        long rowId;
        try {
            rowId = Long.parseLong(idRaw.trim());
        } catch (NumberFormatException e) {
            exchange.getIn().setBody(new IngestErrorResponse("id inválido"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }

        TenantId tenant = new TenantId(tenantId);
        var row = chatMessageRepository.findByIdAndTenant(rowId, tenant);
        if (row.isEmpty()) {
            exchange.getIn().setBody(new IngestErrorResponse("mensagem não encontrada"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NOT_FOUND.value());
            return;
        }
        ChatMessage m = row.get();
        if (m.role() != ChatMessageRole.ASSISTANT || m.status() != ChatMessageStatus.ERROR) {
            exchange.getIn().setBody(new IngestErrorResponse("apenas mensagens ASSISTANT com status ERROR podem ser reenviadas"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.CONFLICT.value());
            return;
        }

        chatMessageRepository.updateStatus(rowId, ChatMessageStatus.RECEIVED);
        whatsAppOutboundPort.sendMessage(m.tenantId(), m.phoneNumber(), m.content(), rowId);
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private static ChatMessageItemResponse toItem(ChatMessage m) {
        long id = m.id() != null ? m.id() : 0L;
        return new ChatMessageItemResponse(
                id,
                m.tenantId().value(),
                m.phoneNumber(),
                m.contactDisplayName(),
                m.contactProfilePicUrl(),
                m.detectedIntent(),
                m.role().name(),
                m.content(),
                m.status().name(),
                DateTimeFormatter.ISO_INSTANT.format(m.timestamp()));
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
