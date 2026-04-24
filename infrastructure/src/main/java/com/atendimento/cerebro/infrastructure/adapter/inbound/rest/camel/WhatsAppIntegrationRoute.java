package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.InboundWhatsAppDeduperPort;
import com.atendimento.cerebro.application.port.out.IntentDetectionPort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.application.service.LeadScoringService;
import com.atendimento.cerebro.infrastructure.analytics.ChatAnalyticsAfterTurnNotifier;
import com.atendimento.cerebro.infrastructure.analytics.PrimaryIntentTurnNotifier;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(200)
public class WhatsAppIntegrationRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppIntegrationRoute.class);
    private static final String INTERNAL_CONFIRMATION_SENT_MARKER = "cliente recebeu a confirmação automática no whatsapp";

    private static final String PROP_DECISION = "whatsappDecision";
    private static final String DECISION_CHAT = "CHAT";
    private static final String DECISION_IGNORE = "IGNORE";
    private static final String DECISION_BAD_REQUEST = "BAD_REQUEST";
    private static final String DECISION_NOT_FOUND = "NOT_FOUND";

    private static final String PROP_PHONE_RAW = "whatsappPhoneRaw";
    private static final String PROP_TENANT_ID = "whatsappTenantId";
    private static final String PROP_INBOUND_USER_TEXT = "whatsappInboundUserText";

    /** Cópia do {@link ChatResult} após o chat — usada pelo analytics assíncrono. */
    private static final String PROP_CHAT_RESULT = "whatsappChatResult";

    /** Máximo de mensagens anteriores (USER/ASSISTANT) enviadas ao modelo, excluindo o turno atual. */
    private static final int WHATSAPP_HISTORY_MAX_TURNS = 15;

    private static final int WHATSAPP_HISTORY_FETCH = WHATSAPP_HISTORY_MAX_TURNS + 1;

    private static final Duration WHATSAPP_HISTORY_MAX_AGE = Duration.ofDays(2);

    /**
     * Fila SEDA (in-process): o REST não pode usar dois .to(direct:iguais); com direct+ponte vimos
     * {@link org.apache.camel.component.direct.DirectConsumerNotAvailableException} em runtime.
     */
    private static final String WHATSAPP_WEBHOOK_SEDA = "seda:whatsappWebhookIn?concurrentConsumers=1&size=2000";

    private final ChatUseCase chatUseCase;
    private final WhatsAppTenantLookupPort tenantLookup;
    private final WhatsAppWebhookParser webhookParser;
    private final WhatsAppOutboundPort whatsAppOutboundPort;
    private final InboundWhatsAppDeduperPort inboundWhatsAppDeduper;
    private final ChatMessageRepository chatMessageRepository;
    private final CrmCustomerStorePort crmCustomerStore;
    private final ConversationBotStatePort conversationBotStatePort;
    private final IntentDetectionPort intentDetectionPort;
    private final PrimaryIntentTurnNotifier primaryIntentTurnNotifier;
    private final ChatAnalyticsAfterTurnNotifier chatAnalyticsAfterTurnNotifier;
    private final LeadScoringService leadScoringService;
    private final int circuitTimeoutMs;
    private final ObjectMapper objectMapper;

    public WhatsAppIntegrationRoute(
            ChatUseCase chatUseCase,
            WhatsAppTenantLookupPort tenantLookup,
            WhatsAppWebhookParser webhookParser,
            WhatsAppOutboundPort whatsAppOutboundPort,
            InboundWhatsAppDeduperPort inboundWhatsAppDeduper,
            ChatMessageRepository chatMessageRepository,
            CrmCustomerStorePort crmCustomerStore,
            ConversationBotStatePort conversationBotStatePort,
            LeadScoringService leadScoringService,
            @Autowired(required = false) IntentDetectionPort intentDetectionPort,
            @Autowired(required = false) PrimaryIntentTurnNotifier primaryIntentTurnNotifier,
            @Autowired(required = false) ChatAnalyticsAfterTurnNotifier chatAnalyticsAfterTurnNotifier,
            ObjectMapper objectMapper,
            @Value("${chat.circuit.timeout-ms:15000}") int circuitTimeoutMs) {
        this.chatUseCase = chatUseCase;
        this.tenantLookup = tenantLookup;
        this.webhookParser = webhookParser;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
        this.inboundWhatsAppDeduper = inboundWhatsAppDeduper;
        this.chatMessageRepository = chatMessageRepository;
        this.crmCustomerStore = crmCustomerStore;
        this.conversationBotStatePort = conversationBotStatePort;
        this.leadScoringService = leadScoringService;
        this.intentDetectionPort = intentDetectionPort;
        this.primaryIntentTurnNotifier = primaryIntentTurnNotifier;
        this.chatAnalyticsAfterTurnNotifier = chatAnalyticsAfterTurnNotifier;
        this.objectMapper = objectMapper;
        this.circuitTimeoutMs = circuitTimeoutMs;
    }

    @Override
    public void configure() {
        // @formatter:off
        from(WHATSAPP_WEBHOOK_SEDA)
                .routeId("whatsappWebhook")
                .process(this::analisarPedido)
                .choice()
                    .when(simple("${exchangeProperty." + PROP_DECISION + "} == '" + DECISION_CHAT + "'"))
                        .process(this::montarComando)
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .timeoutEnabled(true)
                                .timeoutDuration(circuitTimeoutMs)
                            .end()
                            .process(this::executarChatDepoisPrepararSucesso)
                        .onFallback()
                            .process(this::respostaFallback)
                        .end()
                .end();
        // @formatter:on

        // Dois POST no REST; ambos para a mesma fila SEDA (sem validação "duplicate direct" e sem ponte).
        rest("/v1/whatsapp/webhook")
                .post()
                .bindingMode(RestBindingMode.off)
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("seda:whatsappWebhookIn")
                .post("/{event}")
                .bindingMode(RestBindingMode.off)
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .to("seda:whatsappWebhookIn");
    }

    private void analisarPedido(Exchange exchange) {
        String raw = bodyAsUtf8String(exchange);
        if (raw.isBlank()) {
            exchange.setProperty(PROP_DECISION, DECISION_IGNORE);
            exchange.getIn().setBody(new WhatsAppWebhookResponse("ignored"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
            return;
        }
        final JsonNode root;
        try {
            root = parseWebhookJsonLenient(raw);
        } catch (JsonProcessingException e) {
            LOG.warn("whatsapp webhook: JSON inválido ({})", e.getOriginalMessage());
            exchange.setProperty(PROP_DECISION, DECISION_BAD_REQUEST);
            exchange.getIn().setBody(new IngestErrorResponse("corpo JSON inválido"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        WhatsAppWebhookParser.Incoming parsed = webhookParser.parse(root);
        if (parsed instanceof WhatsAppWebhookParser.Incoming.Ignored) {
            exchange.setProperty(PROP_DECISION, DECISION_IGNORE);
            exchange.getIn().setBody(new WhatsAppWebhookResponse("ignored"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
            return;
        }
        if (parsed instanceof WhatsAppWebhookParser.Incoming.TextMessage tm) {
            if (tm.text() == null || tm.text().isBlank()) {
                exchange.setProperty(PROP_DECISION, DECISION_BAD_REQUEST);
                exchange.getIn().setBody(new IngestErrorResponse("mensagem de texto é obrigatória"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
                return;
            }
            String digits = onlyDigits(tm.fromRaw());
            if (digits.isEmpty()) {
                exchange.setProperty(PROP_DECISION, DECISION_BAD_REQUEST);
                exchange.getIn().setBody(new IngestErrorResponse("número do remetente inválido"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
                return;
            }
            var tenant = tenantLookup.findTenantIdByWhatsAppNumber(digits);
            if (tenant.isEmpty()
                    && tm.evolutionLineDigits() != null
                    && !tm.evolutionLineDigits().isBlank()) {
                tenant = tenantLookup.findTenantIdByWhatsAppNumber(tm.evolutionLineDigits());
            }
            if (tenant.isEmpty()) {
                exchange.setProperty(PROP_DECISION, DECISION_NOT_FOUND);
                exchange.getIn().setBody(new IngestErrorResponse("número WhatsApp sem tenant associado"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.NOT_FOUND.value());
                return;
            }
            if (tm.providerMessageId() != null && !tm.providerMessageId().isBlank()) {
                if (!inboundWhatsAppDeduper.tryClaimInboundMessage(
                        tenant.get().value(), tm.providerMessageId().strip())) {
                    exchange.setProperty(PROP_DECISION, DECISION_IGNORE);
                    exchange.getIn().setBody(new WhatsAppWebhookResponse("ignored"));
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
                    return;
                }
            }
            exchange.setProperty(PROP_PHONE_RAW, tm.fromRaw());
            exchange.setProperty(PROP_TENANT_ID, tenant.get().value());
            persistInboundUserMessage(tenant.get(), tm);
            if (!conversationBotStatePort.isBotEnabled(tenant.get(), tm.fromRaw())) {
                exchange.getIn().setBody(new WhatsAppWebhookResponse("human_handoff"));
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
                return;
            }
            exchange.setProperty(PROP_DECISION, DECISION_CHAT);
            exchange.getIn().setBody(tm);
            return;
        }
        throw new IllegalStateException("Unexpected WhatsApp parse result: " + parsed);
    }

    /**
     * Aceita JSON normal ou variantes com escapes extra (ex.: {@code {\"key\":...}}) que alguns clientes enviam.
     */
    private JsonNode parseWebhookJsonLenient(String raw) throws JsonProcessingException {
        String s = raw.strip();
        if (s.isEmpty()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(s);
        } catch (JsonProcessingException first) {
            if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                String inner = objectMapper.readValue(s, String.class);
                return objectMapper.readTree(inner);
            }
            if (s.length() >= 2 && s.charAt(0) == '{' && s.charAt(1) == '\\') {
                return objectMapper.readTree(s.replace("\\\"", "\"").replace("\\\\", "\\"));
            }
            throw first;
        }
    }

    private static String bodyAsUtf8String(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return "";
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (body instanceof String str) {
            return str;
        }
        if (body instanceof InputStream in) {
            try {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }
        return body.toString();
    }

    private void montarComando(Exchange exchange) {
        WhatsAppWebhookParser.Incoming.TextMessage tm =
                exchange.getIn().getBody(WhatsAppWebhookParser.Incoming.TextMessage.class);
        exchange.setProperty(PROP_INBOUND_USER_TEXT, tm.text().strip());
        String digits = onlyDigits(tm.fromRaw());
        // Tenant já resolvido em analisarPedido (inclui fallback evolutionLineDigits ≠ fromRaw do interlocutor).
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new IllegalStateException("whatsapp webhook: tenantId em falta após analisarPedido");
        }
        List<Message> priorTurns = loadWhatsAppHistoryPriorTurns(tenantIdStr, tm.fromRaw(), tm.text().strip());
        exchange.getIn()
                .setBody(
                        new ChatCommand(
                                new TenantId(tenantIdStr),
                                new ConversationId("wa-" + digits),
                                tm.text().strip(),
                                null,
                                AiChatProvider.GEMINI,
                                priorTurns));
    }

    /**
     * Lê as últimas mensagens em {@code chat_message} (limite temporal + contagem), em ordem cronológica
     * crescente, excluindo o turno USER recém-persistido que corresponde à mensagem actual.
     */
    private List<Message> loadWhatsAppHistoryPriorTurns(
            String tenantIdStr, String phoneRaw, String currentUserText) {
        if (tenantIdStr == null || tenantIdStr.isBlank() || phoneRaw == null || phoneRaw.isBlank()) {
            return List.of();
        }
        try {
            TenantId tenantId = new TenantId(tenantIdStr);
            Instant notBefore = Instant.now().minus(WHATSAPP_HISTORY_MAX_AGE);
            List<ChatMessage> rows =
                    chatMessageRepository.findRecentForTenantAndPhone(
                            tenantId, phoneRaw, notBefore, WHATSAPP_HISTORY_FETCH);
            List<ChatMessage> asc = new ArrayList<>(rows);
            Collections.reverse(asc);
            if (!asc.isEmpty()) {
                ChatMessage last = asc.get(asc.size() - 1);
                if (last.role() == ChatMessageRole.USER && last.content().equals(currentUserText.strip())) {
                    asc.remove(asc.size() - 1);
                }
            }
            if (asc.size() > WHATSAPP_HISTORY_MAX_TURNS) {
                asc = new ArrayList<>(asc.subList(asc.size() - WHATSAPP_HISTORY_MAX_TURNS, asc.size()));
            }
            List<Message> out = new ArrayList<>(asc.size());
            for (ChatMessage cm : asc) {
                out.add(toDomainMessage(cm));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            LOG.warn(
                    "falha ao carregar histórico chat_message para o modelo tenant={} phone={}",
                    tenantIdStr,
                    phoneRaw,
                    e);
            return List.of();
        }
    }

    private static Message toDomainMessage(ChatMessage cm) {
        MessageRole role =
                switch (cm.role()) {
                    case USER -> MessageRole.USER;
                    case ASSISTANT -> MessageRole.ASSISTANT;
                };
        return new Message(role, cm.content(), cm.timestamp());
    }

    /**
     * Persiste a mensagem inbound logo após parse + resolução do tenant, antes do circuito de chat.
     */
    private void persistInboundUserMessage(TenantId tenantId, WhatsAppWebhookParser.Incoming.TextMessage tm) {
        String phone = tm.fromRaw();
        if (phone == null || phone.isBlank()) {
            return;
        }
        try {
            chatMessageRepository.save(
                    new ChatMessage(
                            null,
                            tenantId,
                            phone,
                            ChatMessageRole.USER,
                            tm.text().strip(),
                            ChatMessageStatus.SENT,
                            Instant.now(),
                            tm.contactDisplayName(),
                            tm.contactProfilePicUrl(),
                            null));
        } catch (Exception e) {
            LOG.warn(
                    "falha ao persistir mensagem USER no histórico tenant={} phone={}",
                    tenantId.value(),
                    phone,
                    e);
            return;
        }
        try {
            leadScoringService.recalculateAndPersist(tenantId, phone);
        } catch (RuntimeException e) {
            LOG.debug("lead score após mensagem USER ignorado: {}", e.toString());
        }
        String digits = onlyDigits(phone);
        if (!digits.isEmpty()) {
            try {
                crmCustomerStore.ensureOnConversationStart(
                        tenantId,
                        "wa-" + digits,
                        Optional.ofNullable(tm.contactDisplayName()).filter(s -> !s.isBlank()));
            } catch (RuntimeException e) {
                LOG.warn(
                        "falha ao garantir CRM tenant={} phone={}: {}",
                        tenantId.value(),
                        phone,
                        e.toString());
            }
        }
    }

    /**
     * Único passo no circuit breaker após {@link #montarComando}: executa o chat, enfileira classificação
     * Gemini em thread de fundo ({@link ChatAnalyticsAfterTurnNotifier}) e prepara a resposta HTTP/WhatsApp.
     */
    private void executarChatDepoisPrepararSucesso(Exchange exchange) {
        ChatCommand command = exchange.getIn().getBody(ChatCommand.class);
        ChatResult result = chatUseCase.chat(command);
        exchange.setProperty(PROP_CHAT_RESULT, result);
        exchange.getIn().setBody(result);
        scheduleChatAnalyticsGemini(exchange);
        prepararRespostaSucesso(exchange);
    }

    private void scheduleChatAnalyticsGemini(Exchange exchange) {
        if (chatAnalyticsAfterTurnNotifier == null) {
            return;
        }
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);
        ChatResult result = exchange.getProperty(PROP_CHAT_RESULT, ChatResult.class);
        if (tenantIdStr == null
                || tenantIdStr.isBlank()
                || phone == null
                || phone.isBlank()
                || result == null) {
            return;
        }
        try {
            chatAnalyticsAfterTurnNotifier.notifyAfterChatTurn(
                    new TenantId(tenantIdStr.strip()), phone.strip(), result.assistantMessage());
        } catch (RuntimeException e) {
            LOG.debug("chat analytics async enqueue skipped: {}", e.toString());
        }
    }

    private void detectAndPersistIntent(Exchange exchange) {
        if (intentDetectionPort == null) {
            return;
        }
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);
        String userText = exchange.getProperty(PROP_INBOUND_USER_TEXT, String.class);
        if (tenantIdStr == null
                || tenantIdStr.isBlank()
                || phone == null
                || phone.isBlank()
                || userText == null
                || userText.isBlank()) {
            return;
        }
        try {
            intentDetectionPort
                    .detectIntent(new TenantId(tenantIdStr.strip()), userText)
                    .ifPresent(
                            intent -> chatMessageRepository.updateDetectedIntentForLatestUser(
                                    new TenantId(tenantIdStr.strip()), phone.strip(), intent));
        } catch (Exception e) {
            LOG.debug("whatsapp intent detection skipped: {}", e.toString());
        }
    }

    private void prepararRespostaSucesso(Exchange exchange) {
        detectAndPersistIntent(exchange);
        ChatResult result = exchange.getIn().getBody(ChatResult.class);
        enviarRespostaWhatsApp(exchange, result);
        notifyPrimaryIntentAnalytics(exchange);
        exchange.getIn().setBody(new WhatsAppWebhookResponse("processed"));
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void notifyPrimaryIntentAnalytics(Exchange exchange) {
        if (primaryIntentTurnNotifier == null) {
            return;
        }
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);
        if (tenantIdStr == null || tenantIdStr.isBlank() || phone == null || phone.isBlank()) {
            return;
        }
        try {
            primaryIntentTurnNotifier.notifyTurnCompleted(new TenantId(tenantIdStr.strip()), phone.strip());
        } catch (RuntimeException e) {
            LOG.debug("primary intent analytics enqueue skipped: {}", e.toString());
        }
    }

    private void respostaFallback(Exchange exchange) {
        Throwable failure = exchange.getException();
        if (failure == null) {
            failure = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        boolean timedOut = isLikelyTimeout(failure);
        String failureClass = failure != null ? failure.getClass().getName() : "unknown";

        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);

        if (timedOut) {
            exchange.getIn().setBody(new ChatResult(ChatFallbackMessages.TIMEOUT));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.GATEWAY_TIMEOUT.value());
            LOG.warn(
                    "whatsapp circuit fallback (timeout) phone={} failureClass={}",
                    phone,
                    failureClass);
        } else {
            exchange.getIn().setBody(new ChatResult(ChatFallbackMessages.MAINTENANCE));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.SERVICE_UNAVAILABLE.value());
            if (failure != null) {
                LOG.warn(
                        "whatsapp circuit fallback (erro) phone={} failureClass={}",
                        phone,
                        failureClass,
                        failure);
            } else {
                LOG.warn("whatsapp circuit fallback (erro) phone={} failureClass={}", phone, failureClass);
            }
        }
        enviarRespostaWhatsApp(exchange, exchange.getIn().getBody(ChatResult.class));
    }

    /** Delega para {@link WhatsAppOutboundPort} → {@code direct:processWhatsAppResponse} (persistência ASSISTANT lá). */
    private void enviarRespostaWhatsApp(Exchange exchange, ChatResult result) {
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);
        if (tenantIdStr == null || phone == null || result == null || result.assistantMessage() == null) {
            return;
        }
        try {
            if (!result.assistantMessage().isBlank() && !isInternalTechnicalAssistantMessage(result.assistantMessage())) {
                whatsAppOutboundPort.sendMessage(
                        new TenantId(tenantIdStr), phone, result.assistantMessage(), result.whatsAppInteractive());
            }
            for (String extra : result.additionalOutboundMessages()) {
                whatsAppOutboundPort.sendMessage(new TenantId(tenantIdStr), phone, extra);
            }
        } catch (Exception e) {
            LOG.warn(
                    "falha ao despachar resposta WhatsApp outbound tenant={} phone={}",
                    tenantIdStr,
                    phone,
                    e);
        }
    }

    private static boolean isInternalTechnicalAssistantMessage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains(INTERNAL_CONFIRMATION_SENT_MARKER);
    }

    private static boolean isLikelyTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String name = c.getClass().getName();
            if (name.contains("Timeout") || name.contains("TimeLimiter")) {
                return true;
            }
        }
        return false;
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
}
