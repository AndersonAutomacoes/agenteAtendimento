package com.atendimento.cerebro.infrastructure.notification;

import com.atendimento.cerebro.application.event.AppointmentConfirmedEvent;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.WhatsAppOutboundRoutes;
import com.atendimento.cerebro.infrastructure.adapter.out.whatsapp.EvolutionOutboundHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Notificação assíncrona pós-confirmação de agendamento: Evolution (POST directo com auditoria de {@code messageId}) ou
 * Meta/simulado via {@link WhatsAppOutboundPort} (rotas Camel {@code direct:processWhatsAppResponse}).
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private static final DateTimeFormatter DATE_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_OUT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_PARSE_H =
            DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_PARSE_HH =
            DateTimeFormatter.ofPattern("HH:mm");

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final EvolutionOutboundHttp evolutionOutboundHttp;
    private final WhatsAppOutboundPort whatsAppOutboundPort;
    private final ObjectMapper objectMapper;
    private final String evolutionBaseUrlOverride;

    public AppointmentNotificationListener(
            TenantConfigurationStorePort tenantConfigurationStore,
            EvolutionOutboundHttp evolutionOutboundHttp,
            WhatsAppOutboundPort whatsAppOutboundPort,
            ObjectMapper objectMapper,
            @Value("${cerebro.whatsapp.evolution.base-url-override:}") String evolutionBaseUrlOverride) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.evolutionOutboundHttp = evolutionOutboundHttp;
        this.whatsAppOutboundPort = whatsAppOutboundPort;
        this.objectMapper = objectMapper;
        this.evolutionBaseUrlOverride = evolutionBaseUrlOverride != null ? evolutionBaseUrlOverride : "";
    }

    @Async
    @EventListener
    public void onAppointmentConfirmed(AppointmentConfirmedEvent event) {
        Long appointmentId = event.appointmentId();
        TenantId tenantId = event.tenantId();
        String phone = event.phoneNumber() != null ? event.phoneNumber().strip() : "";
        if (phone.isEmpty()) {
            LOG.warn(
                    "[appointment-notify-audit] appointmentId={} tenant={} skipped: phoneNumber vazio",
                    appointmentId,
                    tenantId.value());
            return;
        }

        String dateBr = event.date() != null ? event.date().format(DATE_BR) : "";
        String timeBr = normalizeTimeHhMm(event.timeHhMm());
        String body =
                """
                        Confirmação Realizada! 🚗💨
                        Olá, %s! Seu agendamento para %s foi confirmado com sucesso.
                        📅 Data: %s
                        ⏰ Horário: %s

                        Te aguardamos na oficina! Se precisar desmarcar, é só falar comigo por aqui.
                        """
                        .formatted(
                                nullSafe(event.clientName()),
                                nullSafe(event.serviceName()),
                                dateBr,
                                timeBr)
                        .strip();

        TenantConfiguration cfg =
                tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));
        WhatsAppProviderType effective = WhatsAppOutboundRoutes.effectiveProvider(cfg);

        try {
            if (effective == WhatsAppProviderType.EVOLUTION) {
                sendEvolutionWithAudit(cfg, phone, body, appointmentId, tenantId);
            } else {
                whatsAppOutboundPort.sendMessage(tenantId, phone, body);
                LOG.info(
                        "[appointment-notify-audit] appointmentId={} tenant={} channel={} evolutionMessageId=n/a",
                        appointmentId,
                        tenantId.value(),
                        effective.name());
            }
        } catch (Exception e) {
            LOG.error(
                    "[appointment-notify-audit] appointmentId={} tenant={} falha: {}",
                    appointmentId,
                    tenantId.value(),
                    e.toString());
            if (LOG.isDebugEnabled()) {
                LOG.debug("appointment notify stack", e);
            }
        }
    }

    private void sendEvolutionWithAudit(
            TenantConfiguration cfg, String phoneDigits, String text, Long appointmentId, TenantId tenantId)
            throws Exception {
        String baseRaw =
                evolutionBaseUrlOverride.isBlank() ? cfg.whatsappBaseUrl() : evolutionBaseUrlOverride;
        String base = trimTrailingSlash(baseRaw);
        String apiKey = cfg.whatsappApiKey();
        String instanceId = cfg.whatsappInstanceId();
        if (base.isBlank() || apiKey == null || apiKey.isBlank() || instanceId == null || instanceId.isBlank()) {
            LOG.warn(
                    "[appointment-notify-audit] appointmentId={} tenant={} evolution config incompleta — a usar WhatsAppOutboundPort",
                    appointmentId,
                    tenantId.value());
            whatsAppOutboundPort.sendMessage(tenantId, phoneDigits, text);
            return;
        }

        String url = base + "/message/sendText/" + instanceId;
        String json = buildEvolutionSendTextJson(phoneDigits, text);
        HttpResponse<String> res = evolutionOutboundHttp.postJsonResponse(url, apiKey, json);
        int code = res.statusCode();
        String responseBody = res.body();
        if (code >= 200 && code < 300) {
            Optional<String> mid = extractEvolutionMessageId(responseBody);
            LOG.info(
                    "[appointment-notify-audit] appointmentId={} tenant={} evolutionHttp={} evolutionMessageId={} responseSnippet={}",
                    appointmentId,
                    tenantId.value(),
                    code,
                    mid.orElse("unknown"),
                    truncate(responseBody, 400));
        } else {
            LOG.error(
                    "[appointment-notify-audit] appointmentId={} tenant={} evolutionHttp={} erro={}",
                    appointmentId,
                    tenantId.value(),
                    code,
                    truncate(responseBody, 800));
        }
    }

    private String buildEvolutionSendTextJson(String digits, String messageText) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("number", digits);
        root.put("text", messageText != null ? messageText : "");
        return objectMapper.writeValueAsString(root);
    }

    private Optional<String> extractEvolutionMessageId(String json) {
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
            LOG.debug("extractEvolutionMessageId parse: {}", e.toString());
        }
        return Optional.empty();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.strip();
    }

    static String normalizeTimeHhMm(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.strip();
        try {
            return LocalTime.parse(s, TIME_PARSE_H).format(TIME_OUT);
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(s, TIME_PARSE_HH).format(TIME_OUT);
            } catch (DateTimeParseException e2) {
                return s;
            }
        }
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
