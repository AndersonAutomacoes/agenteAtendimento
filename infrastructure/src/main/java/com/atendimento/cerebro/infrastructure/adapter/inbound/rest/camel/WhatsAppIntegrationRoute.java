package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
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

    private static final String PROP_DECISION = "whatsappDecision";
    private static final String DECISION_CHAT = "CHAT";
    private static final String DECISION_IGNORE = "IGNORE";
    private static final String DECISION_BAD_REQUEST = "BAD_REQUEST";
    private static final String DECISION_NOT_FOUND = "NOT_FOUND";

    private static final String PROP_PHONE_RAW = "whatsappPhoneRaw";
    private static final String PROP_TENANT_ID = "whatsappTenantId";

    /**
     * Fila SEDA (in-process): o REST não pode usar dois .to(direct:iguais); com direct+ponte vimos
     * {@link org.apache.camel.component.direct.DirectConsumerNotAvailableException} em runtime.
     */
    private static final String WHATSAPP_WEBHOOK_SEDA = "seda:whatsappWebhookIn?concurrentConsumers=1&size=2000";

    private final ChatUseCase chatUseCase;
    private final WhatsAppTenantLookupPort tenantLookup;
    private final WhatsAppWebhookParser webhookParser;
    private final WhatsAppOutboundPort whatsAppOutboundPort;
    private final int circuitTimeoutMs;
    private final ObjectMapper objectMapper;

    public WhatsAppIntegrationRoute(
            ChatUseCase chatUseCase,
            WhatsAppTenantLookupPort tenantLookup,
            WhatsAppWebhookParser webhookParser,
            WhatsAppOutboundPort whatsAppOutboundPort,
            ObjectMapper objectMapper,
            @Value("${chat.circuit.timeout-ms:15000}") int circuitTimeoutMs) {
        this.chatUseCase = chatUseCase;
        this.tenantLookup = tenantLookup;
        this.webhookParser = webhookParser;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
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
                            .process(this::executarChat)
                            .process(this::prepararRespostaSucesso)
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
            exchange.setProperty(PROP_DECISION, DECISION_CHAT);
            exchange.setProperty(PROP_PHONE_RAW, tm.fromRaw());
            exchange.setProperty(PROP_TENANT_ID, tenant.get().value());
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
        String digits = onlyDigits(tm.fromRaw());
        // Tenant já resolvido em analisarPedido (inclui fallback evolutionLineDigits ≠ fromRaw do interlocutor).
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            throw new IllegalStateException("whatsapp webhook: tenantId em falta após analisarPedido");
        }
        exchange.getIn()
                .setBody(
                        new ChatCommand(
                                new TenantId(tenantIdStr),
                                new ConversationId("wa-" + digits),
                                tm.text().strip()));
    }

    private void executarChat(Exchange exchange) {
        ChatCommand command = exchange.getIn().getBody(ChatCommand.class);
        ChatResult result = chatUseCase.chat(command);
        exchange.getIn().setBody(result);
    }

    private void prepararRespostaSucesso(Exchange exchange) {
        ChatResult result = exchange.getIn().getBody(ChatResult.class);
        enviarRespostaWhatsApp(exchange, result.assistantMessage());
        exchange.getIn().setBody(new WhatsAppWebhookResponse("processed"));
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
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
        enviarRespostaWhatsApp(
                exchange,
                exchange.getIn().getBody(ChatResult.class).assistantMessage());
    }

    private void enviarRespostaWhatsApp(Exchange exchange, String replyText) {
        String tenantIdStr = exchange.getProperty(PROP_TENANT_ID, String.class);
        String phone = exchange.getProperty(PROP_PHONE_RAW, String.class);
        if (tenantIdStr == null || phone == null || replyText == null) {
            return;
        }
        try {
            whatsAppOutboundPort.sendMessage(new TenantId(tenantIdStr), phone, replyText);
        } catch (Exception e) {
            LOG.warn(
                    "falha ao despachar resposta WhatsApp outbound tenant={} phone={}",
                    tenantIdStr,
                    phone,
                    e);
        }
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
