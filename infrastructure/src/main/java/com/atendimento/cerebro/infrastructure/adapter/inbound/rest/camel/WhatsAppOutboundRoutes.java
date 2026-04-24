package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.scheduling.AssistantOutputSanitizer;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.service.LeadScoringService;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import com.atendimento.cerebro.infrastructure.adapter.out.whatsapp.EvolutionOutboundHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envio outbound: {@code direct:processWhatsAppResponse} escolhe Meta, Evolution ou log (SIMULATED / credenciais em falta).
 *
 * <p><strong>Evolution / Baileys:</strong> o endpoint {@code sendButtons} geralmente envia {@code viewOnceMessage} +
 * {@code nativeFlowMessage}. O WhatsApp costuma <em>não</em> mostrar botões de resposta reais no cliente — apenas texto
 * ou “visto uma vez”. Para lista de horários fiável, use {@code cerebro.whatsapp.evolution.send-interactive-buttons=false}
 * (omissão), que envia uma única mensagem de texto formatada.
 */
@Component
@Order(300)
public class WhatsAppOutboundRoutes extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppOutboundRoutes.class);

    private static final String PROP_FB_PATH = "fbGraphPath";

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final ChatMessageRepository chatMessageRepository;
    private final LeadScoringService leadScoringService;
    private final ObjectMapper objectMapper;
    private final EvolutionOutboundHttp evolutionOutboundHttp;
    private final String metaGraphApiVersion;
    /** Se não vazio, substitui {@link TenantConfiguration#whatsappBaseUrl()} ao montar a URL Evolution (ex.: rede Docker). */
    private final String evolutionBaseUrlOverride;

    /**
     * Se {@code true}, chama {@code /message/sendButtons} (botões raramente visíveis no WhatsApp via Baileys). Se {@code
     * false}, envia só {@code sendText} com lista formatada (omissão recomendada).
     */
    private final boolean evolutionSendInteractiveButtons;

    /**
     * Após {@code sendButtons}, duplica a lista em texto simples (só usado com {@link #evolutionSendInteractiveButtons}
     * {@code true}).
     */
    private final boolean evolutionMirrorSlotsAsPlainText;

    /** Fuso do calendário (alinha cabeçalho “hoje/amanhã” na lista premium). */
    private final ZoneId schedulingCalendarZone;

    public WhatsAppOutboundRoutes(
            TenantConfigurationStorePort tenantConfigurationStore,
            ChatMessageRepository chatMessageRepository,
            LeadScoringService leadScoringService,
            ObjectMapper objectMapper,
            EvolutionOutboundHttp evolutionOutboundHttp,
            @Value("${cerebro.whatsapp.meta.graph-api-version:v21.0}") String metaGraphApiVersion,
            @Value("${cerebro.whatsapp.evolution.base-url-override:}") String evolutionBaseUrlOverride,
            @Value("${cerebro.whatsapp.evolution.send-interactive-buttons:false}")
                    boolean evolutionSendInteractiveButtons,
            @Value("${cerebro.whatsapp.evolution.mirror-slots-as-plain-text:false}")
                    boolean evolutionMirrorSlotsAsPlainText,
            @Value("${cerebro.google.calendar.zone:America/Bahia}") String schedulingCalendarZoneId) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.chatMessageRepository = chatMessageRepository;
        this.leadScoringService = leadScoringService;
        this.objectMapper = objectMapper;
        this.evolutionOutboundHttp = evolutionOutboundHttp;
        this.metaGraphApiVersion = metaGraphApiVersion;
        this.evolutionBaseUrlOverride = evolutionBaseUrlOverride != null ? evolutionBaseUrlOverride : "";
        this.evolutionSendInteractiveButtons = evolutionSendInteractiveButtons;
        this.evolutionMirrorSlotsAsPlainText = evolutionMirrorSlotsAsPlainText;
        this.schedulingCalendarZone =
                schedulingCalendarZoneId != null && !schedulingCalendarZoneId.isBlank()
                        ? ZoneId.of(schedulingCalendarZoneId.strip())
                        : ZoneId.of("America/Bahia");
    }

    @Override
    public void configure() {
        // @formatter:off
        from("direct:processWhatsAppResponse")
                .routeId("processWhatsAppResponse")
                .process(this::loadTenantAndHeaders)
                .process(this::persistAssistantMessageBeforeProviderSend)
                .choice()
                    .when(header(WhatsAppOutboundHeaders.PROVIDER).isEqualTo(WhatsAppProviderType.META.name()))
                        .to("direct:sendToMeta")
                    .when(header(WhatsAppOutboundHeaders.PROVIDER).isEqualTo(WhatsAppProviderType.EVOLUTION.name()))
                        .to("direct:sendToEvolution")
                    .otherwise()
                        .to("direct:sendToLog")
                .end();

        from("direct:sendToMeta")
                .routeId("sendToMeta")
                .doTry()
                    .process(this::prepareMetaHttp)
                    .toD("https://graph.facebook.com/${exchangeProperty." + PROP_FB_PATH + "}/messages")
                    .process(this::logMetaResponse)
                .doCatch(Exception.class)
                    .process(this::logOutboundFailure)
                .end();

        from("direct:sendToEvolution")
                .routeId("sendToEvolution")
                .doTry()
                    .process(this::sendEvolutionOutbound)
                .doCatch(Exception.class)
                    .process(this::logOutboundFailure)
                .end();

        from("direct:sendToLog")
                .routeId("sendToLog")
                .process(this::logSimulation);
        // @formatter:on
    }

    private void loadTenantAndHeaders(Exchange exchange) {
        String tenantIdStr = exchange.getIn().getHeader(WhatsAppOutboundHeaders.TENANT_ID, String.class);
        String to = exchange.getIn().getHeader(WhatsAppOutboundHeaders.TO, String.class);
        String text = exchange.getIn().getBody(String.class);
        String safeBody = sanitizeOutboundBody(text);
        exchange.getMessage().setBody(safeBody);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            LOG.warn("whatsapp outbound: tenantId em falta; a usar SIMULATED");
            exchange.getMessage().setHeader(WhatsAppOutboundHeaders.PROVIDER, WhatsAppProviderType.SIMULATED.name());
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TO, to != null ? to : "");
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, safeBody);
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_INTERACTIVE, null);
            return;
        }
        TenantId tenantId = new TenantId(tenantIdStr);
        TenantConfiguration config =
                tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));

        WhatsAppProviderType effective = effectiveProvider(config);
        exchange.getMessage().setHeader(WhatsAppOutboundHeaders.PROVIDER, effective.name());
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, config);
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TO, to != null ? to : "");
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, safeBody);
        Object interactive = exchange.getIn().getHeader(WhatsAppOutboundHeaders.WHATSAPP_INTERACTIVE);
        exchange.setProperty(WhatsAppOutboundHeaders.PROP_WA_INTERACTIVE, interactive);
        if (interactive instanceof WhatsAppInteractiveReply reply
                && reply.slotTimes() != null
                && !reply.slotTimes().isEmpty()
                && effective != WhatsAppProviderType.EVOLUTION) {
            LOG.warn(
                    "whatsapp outbound: payload com botões de horários mas o tenant {} usa provider {}; "
                            + "só Evolution com URL/instância/chave válidos envia botões — a verificar configuração.",
                    tenantIdStr,
                    effective);
        }
    }

    /**
     * Grava ASSISTANT como {@link ChatMessageStatus#RECEIVED} antes do envio, ou reutiliza o id num reenvio.
     */
    private void persistAssistantMessageBeforeProviderSend(Exchange exchange) {
        Long reuseId = exchange.getIn().getHeader(WhatsAppOutboundHeaders.ASSISTANT_MESSAGE_ID, Long.class);
        if (reuseId != null && reuseId > 0) {
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_ASSISTANT_MESSAGE_ID, reuseId);
            return;
        }
        String tenantIdStr = exchange.getIn().getHeader(WhatsAppOutboundHeaders.TENANT_ID, String.class);
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        if (tenantIdStr == null || tenantIdStr.isBlank() || to == null || to.isBlank() || text == null || text.isBlank()) {
            return;
        }
        try {
            long id =
                    chatMessageRepository.insertReturningId(
                            new ChatMessage(
                                    null,
                                    new TenantId(tenantIdStr.strip()),
                                    to.strip(),
                                    ChatMessageRole.ASSISTANT,
                                    text.strip(),
                                    ChatMessageStatus.RECEIVED,
                                    Instant.now(),
                                    null,
                                    null,
                                    null));
            exchange.setProperty(WhatsAppOutboundHeaders.PROP_ASSISTANT_MESSAGE_ID, id);
            try {
                leadScoringService.recalculateAndPersist(new TenantId(tenantIdStr.strip()), to.strip());
            } catch (RuntimeException ex) {
                LOG.debug("lead score após mensagem ASSISTANT ignorado: {}", ex.toString());
            }
        } catch (Exception e) {
            LOG.warn(
                    "falha ao persistir mensagem ASSISTANT (RECEIVED) antes do outbound tenant={} phone={}",
                    tenantIdStr,
                    to,
                    e);
        }
    }

    private void markAssistantSent(Exchange exchange) {
        Long id = exchange.getProperty(WhatsAppOutboundHeaders.PROP_ASSISTANT_MESSAGE_ID, Long.class);
        if (id == null) {
            return;
        }
        try {
            chatMessageRepository.updateStatus(id, ChatMessageStatus.SENT);
        } catch (Exception e) {
            LOG.warn("falha ao marcar mensagem ASSISTANT como SENT id={}", id, e);
        }
    }

    private void markAssistantError(Exchange exchange) {
        Long id = exchange.getProperty(WhatsAppOutboundHeaders.PROP_ASSISTANT_MESSAGE_ID, Long.class);
        if (id == null) {
            return;
        }
        try {
            chatMessageRepository.updateStatus(id, ChatMessageStatus.ERROR);
        } catch (Exception e) {
            LOG.warn("falha ao marcar mensagem ASSISTANT como ERROR id={}", id, e);
        }
    }

    public static WhatsAppProviderType effectiveProvider(TenantConfiguration c) {
        return switch (c.whatsappProviderType()) {
            case META -> (c.whatsappApiKey() != null && !c.whatsappApiKey().isBlank())
                            && (c.whatsappInstanceId() != null && !c.whatsappInstanceId().isBlank())
                    ? WhatsAppProviderType.META
                    : WhatsAppProviderType.SIMULATED;
            case EVOLUTION -> (c.whatsappApiKey() != null && !c.whatsappApiKey().isBlank())
                            && (c.whatsappInstanceId() != null && !c.whatsappInstanceId().isBlank())
                            && (c.whatsappBaseUrl() != null && !c.whatsappBaseUrl().isBlank())
                    ? WhatsAppProviderType.EVOLUTION
                    : WhatsAppProviderType.SIMULATED;
            case SIMULATED -> WhatsAppProviderType.SIMULATED;
        };
    }

    private void prepareMetaHttp(Exchange exchange) throws Exception {
        TenantConfiguration cfg = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, TenantConfiguration.class);
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        String digits = onlyDigits(to);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("recipient_type", "individual");
        root.put("to", digits);
        root.put("type", "text");
        ObjectNode textNode = root.putObject("text");
        textNode.put("preview_url", false);
        textNode.put("body", text != null ? text : "");

        exchange.getMessage().setBody(objectMapper.writeValueAsString(root));
        exchange.getMessage().removeHeaders("*");
        exchange.getMessage().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader("Authorization", "Bearer " + cfg.whatsappApiKey());

        String path = metaGraphApiVersion + "/" + cfg.whatsappInstanceId();
        exchange.setProperty(PROP_FB_PATH, path);
    }

    private void logMetaResponse(Exchange exchange) {
        Integer code = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        LOG.debug("whatsapp Meta HTTP resposta código={}", code);
        markAssistantSent(exchange);
    }

    /**
     * Evolution: envia texto e/ou botões via HTTP cliente (pode ser duas chamadas: verificação + botões).
     */
    private void sendEvolutionOutbound(Exchange exchange) throws Exception {
        TenantConfiguration cfg = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TENANT_CONFIG, TenantConfiguration.class);
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String digits = onlyDigits(to);
        String baseRaw =
                evolutionBaseUrlOverride.isBlank() ? cfg.whatsappBaseUrl() : evolutionBaseUrlOverride;
        String base = trimTrailingSlash(baseRaw);
        String apiKey = cfg.whatsappApiKey();
        String instanceId = cfg.whatsappInstanceId();

        Object raw = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_INTERACTIVE);
        if (raw instanceof WhatsAppInteractiveReply reply
                && reply.slotTimes() != null
                && !reply.slotTimes().isEmpty()) {
            List<String> validTimes = SchedulingSlotCapture.normalizeSlotTimes(reply.slotTimes());
            if (validTimes.isEmpty()) {
                LOG.warn(
                        "Evolution: lista de horários vazia ou inválida após normalização (instance={} to={}); enviando texto de recurso.",
                        instanceId,
                        digits);
                String textJson =
                        buildEvolutionSendTextJson(digits, SchedulingSlotCapture.SLOTS_ALL_OCCUPIED_PT);
                evolutionOutboundHttp.postJson(base + "/message/sendText/" + instanceId, apiKey, textJson);
                exchange.getMessage().setBody("");
                markAssistantSent(exchange);
                return;
            }
            WhatsAppInteractiveReply safeReply =
                    new WhatsAppInteractiveReply(
                            sanitizeOutboundBody(reply.title()),
                            sanitizeOutboundBody(reply.description()),
                            validTimes,
                            sanitizeOutboundBody(reply.verificationText()),
                            reply.requestedDate());
            if (!evolutionSendInteractiveButtons) {
                String body = sanitizeOutboundBody(buildSlotsFormattedPlainText(safeReply, validTimes));
                LOG.info(
                        "Evolution: envio só texto formatado (send-interactive-buttons=false) instance={} to={}",
                        instanceId,
                        digits);
                evolutionOutboundHttp.postJson(
                        base + "/message/sendText/" + instanceId, apiKey, buildEvolutionSendTextJson(digits, body));
                exchange.getMessage().setBody("");
                markAssistantSent(exchange);
                return;
            }
            try {
                String verification = safeReply.verificationText();
                if (verification != null && !verification.isBlank()) {
                    String textJson = buildEvolutionSendTextJson(digits, sanitizeOutboundBody(verification));
                    evolutionOutboundHttp.postJson(base + "/message/sendText/" + instanceId, apiKey, textJson);
                }
                String buttonsJson = buildEvolutionSendButtonsJson(digits, safeReply);
                LOG.info("Evolution sendButtons request JSON (instance={} to={}): {}", instanceId, digits, buttonsJson);
                evolutionOutboundHttp.postJson(base + "/message/sendButtons/" + instanceId, apiKey, buttonsJson);
                if (evolutionMirrorSlotsAsPlainText) {
                    String mirror = sanitizeOutboundBody(buildMirrorSlotsPlainText(validTimes, reply.requestedDate()));
                    String mirrorJson = buildEvolutionSendTextJson(digits, mirror);
                    LOG.debug("Evolution mirror sendText após botões (instance={}): {}", instanceId, mirror);
                    evolutionOutboundHttp.postJson(base + "/message/sendText/" + instanceId, apiKey, mirrorJson);
                }
            } catch (Exception e) {
                LOG.error(
                        "Evolution: falha ao enviar mensagem interativa (sendText/sendButtons) instance={} to={}: {}",
                        instanceId,
                        digits,
                        e.toString());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Evolution sendButtons stack", e);
                }
                String fallback =
                        "Não foi possível mostrar os botões de horário neste momento. Por favor, digite o horário "
                                + "desejado no formato HH:mm (ex.: 09:00).";
                String fallbackJson = buildEvolutionSendTextJson(digits, fallback);
                try {
                    evolutionOutboundHttp.postJson(base + "/message/sendText/" + instanceId, apiKey, fallbackJson);
                } catch (Exception e2) {
                    LOG.error("Evolution: falha também no envio de texto mínimo após erro de botões: {}", e2.toString());
                    throw e2;
                }
            }
        } else {
            String text = sanitizeOutboundBody(exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class));
            String textJson = buildEvolutionSendTextJson(digits, text);
            boolean capture =
                    Boolean.TRUE.equals(
                            exchange.getIn()
                                    .getHeader(WhatsAppOutboundHeaders.CAPTURE_EVOLUTION_MESSAGE_ID, Boolean.class));
            String sendTextUrl = base + "/message/sendText/" + instanceId;
            if (capture) {
                HttpResponse<String> res = evolutionOutboundHttp.postJsonResponse(sendTextUrl, apiKey, textJson);
                int code = res.statusCode();
                String responseBody = res.body();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException(
                            "Evolution HTTP "
                                    + code
                                    + " url="
                                    + sendTextUrl
                                    + " body="
                                    + truncateForEvolutionError(responseBody, 1200));
                }
                Optional<String> mid = parseEvolutionMessageId(responseBody);
                exchange.setProperty(
                        WhatsAppOutboundHeaders.PROP_EVOLUTION_MESSAGE_ID, mid.map(String::strip).orElse(null));
            } else {
                evolutionOutboundHttp.postJson(sendTextUrl, apiKey, textJson);
            }
        }
        exchange.getMessage().setBody("");
        markAssistantSent(exchange);
    }

    private Optional<String> parseEvolutionMessageId(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode key = root.path("key");
            if (key.isObject() && key.has("id")) {
                return Optional.ofNullable(key.get("id").asText(null));
            }
            if (root.hasNonNull("messageId")) {
                return Optional.of(root.get("messageId").asText());
            }
            if (root.hasNonNull("id")) {
                return Optional.of(root.get("id").asText());
            }
        } catch (Exception e) {
            LOG.debug("parseEvolutionMessageId: {}", e.toString());
        }
        return Optional.empty();
    }

    private static String truncateForEvolutionError(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private String buildEvolutionSendTextJson(String digits, String text) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("number", digits);
        root.put("text", text != null ? text : "");
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Lista única em texto, legível em qualquer cliente WhatsApp (recomendado em vez de {@code sendButtons} com
     * Baileys).
     */
    String buildSlotsFormattedPlainText(WhatsAppInteractiveReply reply, List<String> times) {
        if (times == null || times.isEmpty()) {
            return "";
        }
        return SchedulingSlotCapture.buildPremiumFormattedSlotList(
                reply.requestedDate(), schedulingCalendarZone, times);
    }

    /** Espelho após {@code sendButtons}: mesma estética premium (até 16 horários). */
    String buildMirrorSlotsPlainText(List<String> validTimes, LocalDate requestedDate) {
        if (validTimes == null || validTimes.isEmpty()) {
            return "";
        }
        int maxList = 16;
        List<String> slice = validTimes.size() > maxList ? validTimes.subList(0, maxList) : validTimes;
        String core = SchedulingSlotCapture.buildPremiumFormattedSlotList(requestedDate, schedulingCalendarZone, slice);
        if (validTimes.size() > maxList) {
            return core + "\n\n…";
        }
        return core;
    }

    /**
     * Evolution API v2: botões de resposta (máx. 3). Horários extra permanecem no texto da descrição.
     */
    private String buildEvolutionSendButtonsJson(String digits, WhatsAppInteractiveReply reply) throws Exception {
        List<String> times = SchedulingSlotCapture.normalizeSlotTimes(reply.slotTimes());
        if (times.isEmpty()) {
            throw new IllegalStateException("slotTimes vazio após normalização");
        }
        List<String> buttonTimes = times.size() > 3 ? times.subList(0, 3) : times;

        ObjectNode root = objectMapper.createObjectNode();
        root.put("number", digits);
        String title = reply.title() != null && !reply.title().isBlank() ? reply.title() : "Horários disponíveis";
        root.put("title", truncateForWhatsApp(sanitizeOutboundBody(title), 60));
        String desc = reply.description() != null ? reply.description() : "";
        if (times.size() > 3) {
            desc = desc + "\n\nHá mais horários neste dia; pode digitar HH:mm ou usar um dos botões acima.";
        }
        root.put("description", truncateForWhatsApp(sanitizeOutboundBody(desc), 1024));
        root.put("footer", times.size() > 3 ? "Mais horários na descrição." : "Toque num botão.");

        ArrayNode buttons = root.putArray("buttons");
        for (String time : buttonTimes) {
            if (time == null || time.isBlank()) {
                continue;
            }
            ObjectNode b = buttons.addObject();
            b.put("type", "reply");
            b.put("displayText", truncateForWhatsApp(sanitizeOutboundBody(time.strip()), 20));
            b.put("id", "slot_" + time.strip().replace(':', '_'));
        }

        return objectMapper.writeValueAsString(root);
    }

    private static String truncateForWhatsApp(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private void logOutboundFailure(Exchange exchange) {
        Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (ex == null) {
            ex = exchange.getException();
        }
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        LOG.warn("whatsapp outbound falhou (to={}): {}", to, ex != null ? ex.getMessage() : "unknown");
        if (ex != null && LOG.isDebugEnabled()) {
            LOG.debug("whatsapp outbound stack", ex);
        }
        markAssistantError(exchange);
    }

    private void logSimulation(Exchange exchange) {
        String to = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TO, String.class);
        String text = exchange.getProperty(WhatsAppOutboundHeaders.PROP_WA_TEXT, String.class);
        LOG.info("[Simulação WhatsApp para {}]: {}", to, text);
        markAssistantSent(exchange);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
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

    /**
     * Sanitização completa antes de enviar ao Meta/Evolution: apêndices internos, URLs do Calendar e instruções de
     * ferramenta que não devem ser vistas pelo cliente.
     */
    static String sanitizeOutboundBody(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return AssistantOutputSanitizer.stripSquareBracketSegments(text);
    }
}
