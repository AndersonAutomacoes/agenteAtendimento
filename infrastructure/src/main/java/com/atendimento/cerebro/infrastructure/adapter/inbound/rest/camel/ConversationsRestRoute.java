package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/v1/conversations/human-handoff} — desliga o bot e notifica o cliente.
 * {@code POST /api/v1/conversations/enable-bot} — volta a permitir respostas da IA.
 */
@Component
@Order(155)
public class ConversationsRestRoute extends RouteBuilder {

    static final String HANDOFF_CUSTOMER_MESSAGE =
            "Um atendente humano assumiu o chat para prosseguir com seu atendimento.";

    private final ConversationBotStatePort conversationBotStatePort;
    private final WhatsAppOutboundPort whatsAppOutboundPort;

    public ConversationsRestRoute(
            ConversationBotStatePort conversationBotStatePort, WhatsAppOutboundPort whatsAppOutboundPort) {
        this.conversationBotStatePort = conversationBotStatePort;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
    }

    @Override
    public void configure() {
        rest("/v1/conversations")
                .post("/human-handoff")
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(ConversationPhoneBody.class)
                .to("direct:conversationsHumanHandoff")
                .post("/enable-bot")
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(ConversationPhoneBody.class)
                .to("direct:conversationsEnableBot");

        from("direct:conversationsHumanHandoff")
                .routeId("conversationsHumanHandoff")
                .process(this::handleHumanHandoff);

        from("direct:conversationsEnableBot")
                .routeId("conversationsEnableBot")
                .process(this::handleEnableBot);
    }

    private void handleHumanHandoff(Exchange exchange) {
        String tenantId = requireTenantId(exchange);
        if (tenantId == null) {
            return;
        }
        ConversationPhoneBody body = exchange.getIn().getBody(ConversationPhoneBody.class);
        if (body == null || body.phoneNumber() == null || body.phoneNumber().isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("phoneNumber é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        String phone = body.phoneNumber().strip();
        TenantId tenant = new TenantId(tenantId);
        boolean wasEnabled = conversationBotStatePort.isBotEnabled(tenant, phone);
        conversationBotStatePort.setBotEnabled(tenant, phone, false);
        if (wasEnabled) {
            try {
                whatsAppOutboundPort.sendMessage(tenant, phone, HANDOFF_CUSTOMER_MESSAGE);
            } catch (RuntimeException e) {
                exchange.getIn().setBody(new IngestErrorResponse("falha ao enviar notificação WhatsApp"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_GATEWAY.value());
                return;
            }
        }
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private void handleEnableBot(Exchange exchange) {
        String tenantId = requireTenantId(exchange);
        if (tenantId == null) {
            return;
        }
        ConversationPhoneBody body = exchange.getIn().getBody(ConversationPhoneBody.class);
        if (body == null || body.phoneNumber() == null || body.phoneNumber().isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("phoneNumber é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        conversationBotStatePort.enableBotWithResumeContextHint(
                new TenantId(tenantId), body.phoneNumber().strip());
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    private static String requireTenantId(Exchange exchange) {
        String tenantId = exchange.getMessage().getHeader("tenantId", String.class);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        if (tenantId == null || tenantId.isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("tenantId é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return null;
        }
        return tenantId.strip();
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
