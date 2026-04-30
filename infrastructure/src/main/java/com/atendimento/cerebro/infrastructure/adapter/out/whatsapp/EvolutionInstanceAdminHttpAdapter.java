package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import com.atendimento.cerebro.application.port.out.EvolutionInstanceAdminPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EvolutionInstanceAdminHttpAdapter implements EvolutionInstanceAdminPort {

    private static final Logger LOG = LoggerFactory.getLogger(EvolutionInstanceAdminHttpAdapter.class);

    private final EvolutionOutboundHttp evolutionOutboundHttp;
    private final ObjectMapper objectMapper;

    public EvolutionInstanceAdminHttpAdapter(EvolutionOutboundHttp evolutionOutboundHttp, ObjectMapper objectMapper) {
        this.evolutionOutboundHttp = evolutionOutboundHttp;
        this.objectMapper = objectMapper;
    }

    @Override
    public CreateInstanceResult createWhatsappBaileysInstance(
            String evolutionBaseUrl, String apiKey, String instanceName, boolean requestQrcodeInResponse) {
        String base = trimBase(evolutionBaseUrl);
        String url = base + "/instance/create";
        String body =
                "{\"instanceName\":\""
                        + escapeJson(instanceName)
                        + "\",\"qrcode\":"
                        + requestQrcodeInResponse
                        + ",\"integration\":\"WHATSAPP-BAILEYS\"}";
        try {
            var res = evolutionOutboundHttp.postJsonResponse(url, apiKey, body);
            int code = res.statusCode();
            String raw = res.body() != null ? res.body() : "";
            Optional<String> qr = Optional.empty();
            try {
                JsonNode root = objectMapper.readTree(raw);
                qr = extractQrBase64(root);
            } catch (Exception parseEx) {
                LOG.debug("Evolution create parse: {}", parseEx.toString());
            }
            boolean success = code >= 200 && code < 300;
            return new CreateInstanceResult(success, code, raw, qr);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha de rede ao criar instância Evolution: " + e.getMessage(), e);
        }
    }

    @Override
    public void setInstanceWebhook(
            String evolutionBaseUrl,
            String apiKey,
            String instanceName,
            String webhookUrl,
            boolean webhookByEvents) {
        String base = trimBase(evolutionBaseUrl);
        String enc = urlEncodePathSegment(instanceName);
        String url = base + "/webhook/set/" + enc;
        String body;
        try {
            var root = objectMapper.createObjectNode();
            var wh = objectMapper.createObjectNode();
            wh.put("enabled", true);
            wh.put("url", webhookUrl);
            wh.put("webhook_by_events", webhookByEvents);
            var events = objectMapper.createArrayNode();
            events.add("MESSAGES_UPSERT");
            events.add("CONNECTION_UPDATE");
            events.add("QRCODE_UPDATED");
            wh.set("events", events);
            root.set("webhook", wh);
            body = objectMapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        try {
            var res = evolutionOutboundHttp.postJsonResponse(url, apiKey, body);
            int code = res.statusCode();
            if (code < 200 || code >= 300) {
                LOG.warn(
                        "Evolution set webhook HTTP {} instance={} body={}",
                        code,
                        instanceName,
                        truncate(res.body(), 500));
                throw new IllegalStateException("Evolution falhou ao definir webhook HTTP " + code);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Falha de rede ao definir webhook Evolution: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> connectAndFetchQrcodeBase64(
            String evolutionBaseUrl, String apiKey, String instanceName) {
        String base = trimBase(evolutionBaseUrl);
        String enc = urlEncodePathSegment(instanceName);
        String url = base + "/instance/connect/" + enc;
        String body = "{}";
        try {
            var res = evolutionOutboundHttp.postJsonResponse(url, apiKey, body);
            String raw = res.body() != null ? res.body() : "";
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                LOG.debug("Evolution connect HTTP {} body={}", res.statusCode(), truncate(raw, 400));
                try {
                    JsonNode root = objectMapper.readTree(raw);
                    return extractQrBase64(root);
                } catch (Exception ignored) {
                    return Optional.empty();
                }
            }
            JsonNode root = objectMapper.readTree(raw);
            return extractQrBase64(root);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Evolution connect network: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Procura blobs base64 relacionados a QR em respostas heterogéneas da Evolution. */
    static Optional<String> extractQrBase64(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return Optional.empty();
        }
        Optional<String> direct = textIfPresent(root, "base64");
        if (direct.isPresent()) {
            return direct;
        }
        JsonNode qrcode = root.get("qrcode");
        if (qrcode != null && !qrcode.isNull()) {
            Optional<String> b = textIfPresent(qrcode, "base64");
            if (b.isPresent()) {
                return b;
            }
            b = textIfPresent(qrcode, "code");
            if (b.isPresent()) {
                return b;
            }
        }
        JsonNode data = root.get("data");
        if (data != null) {
            Optional<String> b = extractQrBase64(data);
            if (b.isPresent()) {
                return b;
            }
        }
        // array: qualquer elemento com qrcode
        if (root.isArray()) {
            return StreamSupport.stream(root.spliterator(), false)
                    .map(EvolutionInstanceAdminHttpAdapter::extractQrBase64)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
        var it = root.fields();
        while (it.hasNext()) {
            var e = it.next();
            Optional<String> nested = extractQrBase64(e.getValue());
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> textIfPresent(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return Optional.empty();
        }
        String t = node.get(field).asText("").strip();
        return t.isEmpty() ? Optional.empty() : Optional.of(t);
    }

    private static String trimBase(String evolutionBaseUrl) {
        if (evolutionBaseUrl == null || evolutionBaseUrl.isBlank()) {
            throw new IllegalArgumentException("evolutionBaseUrl vazio");
        }
        String u = evolutionBaseUrl.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String urlEncodePathSegment(String instanceName) {
        return URLEncoder.encode(instanceName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
