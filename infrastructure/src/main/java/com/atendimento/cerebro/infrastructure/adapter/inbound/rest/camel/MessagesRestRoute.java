package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.net.URLDecoder;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /api/v1/messages?tenantId=...} — histórico recente e {@code botEnabledByPhone} (monitorização WhatsApp).
 * {@code POST /api/v1/messages/human-reply?tenantId=...} — envio WhatsApp pelo humano (bot desligado).
 * {@code POST /api/v1/messages/{id}/retry?tenantId=...} — reenvio de uma resposta ASSISTANT em ERROR.
 */
@Component
@Order(150)
public class MessagesRestRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(MessagesRestRoute.class);

    private static final int DEFAULT_LIMIT = 50;

    /** Limite alinhado a mensagens de texto WhatsApp. */
    private static final int HUMAN_REPLY_MAX_CHARS = 4096;

    private final ChatMessageRepository chatMessageRepository;
    private final WhatsAppOutboundPort whatsAppOutboundPort;
    private final ConversationBotStatePort conversationBotStatePort;
    private final ConversationContextStorePort conversationContextStore;

    public MessagesRestRoute(
            ChatMessageRepository chatMessageRepository,
            WhatsAppOutboundPort whatsAppOutboundPort,
            ConversationBotStatePort conversationBotStatePort,
            ConversationContextStorePort conversationContextStore) {
        this.chatMessageRepository = chatMessageRepository;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
        this.conversationBotStatePort = conversationBotStatePort;
        this.conversationContextStore = conversationContextStore;
    }

    @Override
    public void configure() {
        rest("/v1/messages")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:messagesGet")
                .post("/human-reply")
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(HumanReplyBody.class)
                .to("direct:messagesHumanReply")
                .post("/{id}/retry")
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("direct:messagesRetry");

        from("direct:messagesGet")
                .routeId("messagesGet")
                .process(this::handleGet);

        from("direct:messagesHumanReply")
                .routeId("messagesHumanReply")
                .process(this::handleHumanReply);

        from("direct:messagesRetry")
                .routeId("messagesRetry")
                .process(this::handleRetry);
    }

    private void handleGet(Exchange exchange) {
        String tenantId = requireAuthorizedTenant(exchange);
        if (tenantId == null) {
            return;
        }
        TenantId tenant = new TenantId(tenantId);
        List<ChatMessage> rows = chatMessageRepository.findLastByTenantId(tenant, DEFAULT_LIMIT);
        List<ChatMessageItemResponse> items = rows.stream().map(MessagesRestRoute::toItem).toList();
        Set<String> phones = new HashSet<>();
        for (ChatMessage m : rows) {
            phones.add(m.phoneNumber());
        }
        var botEnabledByPhone = conversationBotStatePort.resolveBotEnabledForPhones(tenant, phones);
        exchange.getMessage().setBody(new ChatMessagesListResponse(items, botEnabledByPhone));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handleHumanReply(Exchange exchange) {
        String tenantId = requireAuthorizedTenant(exchange);
        if (tenantId == null) {
            return;
        }

        HumanReplyBody body = exchange.getIn().getBody(HumanReplyBody.class);
        if (body == null
                || body.phoneNumber() == null
                || body.phoneNumber().isBlank()
                || body.text() == null
                || body.text().isBlank()) {
            exchange.getIn().setBody(new IngestErrorResponse("phoneNumber e text são obrigatórios"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        String phone = body.phoneNumber().strip();
        String text = body.text().strip();
        if (text.length() > HUMAN_REPLY_MAX_CHARS) {
            exchange.getIn()
                    .setBody(
                            new IngestErrorResponse(
                                    "text excede o limite de " + HUMAN_REPLY_MAX_CHARS + " caracteres"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }

        TenantId tenant = new TenantId(tenantId);
        if (conversationBotStatePort.isBotEnabled(tenant, phone)) {
            exchange.getIn()
                    .setBody(
                            new IngestErrorResponse(
                                    "o bot ainda está ativo para este contacto; assuma a conversa antes de enviar"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.CONFLICT.value());
            return;
        }

        try {
            whatsAppOutboundPort.sendMessage(tenant, phone, text);
        } catch (RuntimeException e) {
            exchange.getIn().setBody(new IngestErrorResponse("falha ao enviar mensagem WhatsApp"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_GATEWAY.value());
            return;
        }
        appendHumanAdminToConversationContext(tenant, phone, text);
        exchange.getMessage().setBody(null);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NO_CONTENT.value());
    }

    /**
     * Regista a intervenção humana em {@code conversation_message} (sender HUMAN_ADMIN) para o histórico
     * híbrido do Gemini, alinhado ao {@code conversation_id} WhatsApp {@code wa-&lt;dígitos&gt;}.
     */
    private void appendHumanAdminToConversationContext(TenantId tenantId, String phoneRaw, String text) {
        String digits = onlyDigits(phoneRaw);
        if (digits.isEmpty()) {
            return;
        }
        try {
            ConversationId cid = new ConversationId("wa-" + digits);
            ConversationContext ctx =
                    conversationContextStore
                            .load(tenantId, cid)
                            .orElseGet(
                                    () ->
                                            ConversationContext.builder()
                                                    .tenantId(tenantId)
                                                    .conversationId(cid)
                                                    .build());
            ConversationContext updated = ctx.append(Message.humanAdminMessage(text));
            conversationContextStore.save(updated);
        } catch (Exception e) {
            LOG.warn(
                    "falha ao sincronizar mensagem humana em conversation_message tenant={} conversationId=wa-{}",
                    tenantId.value(),
                    digits,
                    e);
        }
    }

    private static String onlyDigits(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void handleRetry(Exchange exchange) {
        String tenantId = requireAuthorizedTenant(exchange);
        if (tenantId == null) {
            return;
        }

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
        String body =
                m.role() == ChatMessageRole.ASSISTANT
                        ? SchedulingUserReplyNormalizer.stripInternalSlotAppendix(m.content())
                        : m.content();
        return new ChatMessageItemResponse(
                id,
                m.tenantId().value(),
                m.phoneNumber(),
                m.contactDisplayName(),
                m.contactProfilePicUrl(),
                m.detectedIntent(),
                m.role().name(),
                body,
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

    private static String requireAuthorizedTenant(Exchange exchange) {
        String requested = exchange.getMessage().getHeader("tenantId", String.class);
        if (requested == null || requested.isBlank()) {
            requested = parseQueryParam(exchange.getMessage().getHeader(Exchange.HTTP_QUERY, String.class), "tenantId");
        }
        return CamelAuthSupport.authorizedTenantOrAbort(exchange, requested);
    }
}
