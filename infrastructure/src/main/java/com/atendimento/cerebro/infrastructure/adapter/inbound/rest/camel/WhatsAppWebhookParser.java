package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Extrai remetente e texto de:
 * <ul>
 *   <li>WhatsApp Cloud API (Meta): envelope {@code entry/changes/...}</li>
 *   <li>Evolution API v2: {@code event = messages.upsert}, corpo em {@code data} (remoteJid, message, fromMe)</li>
 *   <li>JSON simples (testes): {@code {"from":"...","text":"..."}} ou {@code message} em vez de {@code text}</li>
 * </ul>
 */
@Component
public class WhatsAppWebhookParser {

    private static final String EVOLUTION_MESSAGES_UPSERT = "messages.upsert";

    public sealed interface Incoming permits Incoming.Ignored, Incoming.TextMessage {
        record Ignored() implements Incoming {}

        /**
         * @param fromRaw Só dígitos do interlocutor (para resposta Evolution/Meta e conversa).
         * @param evolutionLineDigits Dígitos do {@code sender} no envelope Evolution (linha / instância), ou {@code null}.
         */
        record TextMessage(String fromRaw, String text, String evolutionLineDigits) implements Incoming {
            public TextMessage(String fromRaw, String text) {
                this(fromRaw, text, null);
            }
        }
    }

    public Incoming parse(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return new Incoming.Ignored();
        }
        if (root.has("event")) {
            return parseEvolutionEvent(root);
        }
        if (root.has("entry")) {
            return parseMetaEnvelope(root);
        }
        return parseSimple(root);
    }

    private static Incoming parseEvolutionEvent(JsonNode root) {
        String ev = root.path("event").asText("").strip();
        if (EVOLUTION_MESSAGES_UPSERT.equalsIgnoreCase(ev) || "MESSAGES_UPSERT".equalsIgnoreCase(ev)) {
            return parseEvolutionMessagesUpsert(root);
        }
        return new Incoming.Ignored();
    }

    /**
     * Formato típico Evolution v2: {@code data.key.remoteJid}, {@code data.key.fromMe},
     * texto em {@code data.message.conversation} ou {@code extendedTextMessage.text}, etc.
     */
    private static Incoming parseEvolutionMessagesUpsert(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return new Incoming.Ignored();
        }
        JsonNode key = data.path("key");
        if (key.path("fromMe").asBoolean(false)) {
            return new Incoming.Ignored();
        }
        String remoteJid = key.path("remoteJid").asText("").strip();
        if (remoteJid.isBlank() || remoteJid.endsWith("@g.us")) {
            return new Incoming.Ignored();
        }
        String fromDigits = resolveEvolutionReplyDigits(key, remoteJid);
        if (fromDigits.isEmpty()) {
            return new Incoming.Ignored();
        }
        JsonNode message = data.path("message");
        String text = extractEvolutionMessageText(message, data.path("messageType").asText(""));
        if (text == null || text.isBlank()) {
            return new Incoming.Ignored();
        }
        String lineDigits = evolutionWebhookLineDigits(root);
        return new Incoming.TextMessage(fromDigits, text.strip(), lineDigits);
    }

    /**
     * Com {@code addressingMode: lid}, o {@code remoteJid} vem como {@code …@lid}; o número PN está em {@code remoteJidAlt}.
     */
    private static String resolveEvolutionReplyDigits(JsonNode key, String remoteJid) {
        if (remoteJid.endsWith("@lid")) {
            String alt = key.path("remoteJidAlt").asText("").strip();
            if (!alt.isBlank() && !alt.endsWith("@g.us")) {
                return digitsOnly(beforeAt(alt));
            }
        }
        return digitsOnly(beforeAt(remoteJid));
    }

    private static String evolutionWebhookLineDigits(JsonNode root) {
        String sender = root.path("sender").asText("").strip();
        if (sender.isBlank()) {
            return null;
        }
        String d = digitsOnly(beforeAt(sender));
        return d.isEmpty() ? null : d;
    }

    private static String beforeAt(String remoteJid) {
        int at = remoteJid.indexOf('@');
        return at >= 0 ? remoteJid.substring(0, at) : remoteJid;
    }

    private static String extractEvolutionMessageText(JsonNode message, String messageType) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return "";
        }
        if (message.has("conversation")) {
            return message.path("conversation").asText("");
        }
        if (message.has("extendedTextMessage")) {
            return message.path("extendedTextMessage").path("text").asText("");
        }
        if (message.has("imageMessage")) {
            return message.path("imageMessage").path("caption").asText("");
        }
        if (message.has("videoMessage")) {
            return message.path("videoMessage").path("caption").asText("");
        }
        if ("protocolMessage".equals(messageType) || message.has("protocolMessage")) {
            return "";
        }
        return "";
    }

    private static String digitsOnly(String raw) {
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

    private static Incoming parseSimple(JsonNode root) {
        if (!root.has("from")) {
            return new Incoming.Ignored();
        }
        String from = root.get("from").asText("").strip();
        String text = "";
        if (root.has("text")) {
            text = root.get("text").asText("");
        } else if (root.has("message")) {
            text = root.get("message").asText("");
        }
        if (from.isBlank()) {
            return new Incoming.Ignored();
        }
        return new Incoming.TextMessage(from, text);
    }

    private static Incoming parseMetaEnvelope(JsonNode root) {
        for (JsonNode entry : root.withArray("entry")) {
            for (JsonNode change : entry.withArray("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode message : value.withArray("messages")) {
                    if (!"text".equals(message.path("type").asText())) {
                        continue;
                    }
                    String from = message.path("from").asText("").strip();
                    String body = message.path("text").path("body").asText("");
                    if (!from.isBlank() && !body.isBlank()) {
                        return new Incoming.TextMessage(from, body);
                    }
                }
            }
        }
        return new Incoming.Ignored();
    }
}
