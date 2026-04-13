package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifica intenção e sentimento da conversa (Gemini) e persiste em {@code chat_analytics}.
 */
public class AnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsService.class);

    static final String CLASSIFICATION_SYSTEM_PROMPT =
            """
            Com base nas últimas mensagens desta conversa de WhatsApp, classifique a intenção principal \
            do cliente e o sentimento geral da conversa.

            Intenção (uma só): Orçamento, Agendamento, Suporte, Venda ou Outros.
            Sentimento (um só): Positivo, Neutro ou Negativo.

            Responda em exatamente duas linhas, neste formato e nesta ordem:
            INTENCAO: <valor>
            SENTIMENTO: <valor>

            Use apenas os valores listados acima, sem aspas ou texto extra.""";

    private final ChatAnalyticsRepository chatAnalyticsRepository;
    private final AIEnginePort aiEngine;
    private final CrmLeadIntentUpdater crmLeadIntentUpdater;
    private final LeadScoringService leadScoringService;
    private final boolean enabled;
    private final int maxTranscriptChars;

    public AnalyticsService(
            ChatAnalyticsRepository chatAnalyticsRepository,
            AIEnginePort aiEngine,
            CrmLeadIntentUpdater crmLeadIntentUpdater,
            LeadScoringService leadScoringService,
            boolean enabled,
            int maxTranscriptChars) {
        this.chatAnalyticsRepository = chatAnalyticsRepository;
        this.aiEngine = aiEngine;
        this.crmLeadIntentUpdater = crmLeadIntentUpdater;
        this.leadScoringService = leadScoringService;
        this.enabled = enabled;
        this.maxTranscriptChars = Math.min(Math.max(maxTranscriptChars, 1024), 32_000);
    }

    /**
     * Classifica e faz upsert de uma linha {@code chat_analytics} para o par tenant + telefone.
     *
     * @param recentChronological mensagens recentes em ordem cronológica crescente (sem duplicar o turno
     *     ASSISTENTE actual se ainda não persistido)
     * @param latestAssistantText texto da resposta do assistente neste turno (pode ser vazio)
     */
    public void analyzeAndUpsert(
            TenantId tenantId,
            String phoneNumber,
            List<ChatMessage> recentChronological,
            String latestAssistantText) {
        if (!enabled || tenantId == null || phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String phone = phoneNumber.strip();
        String transcript = buildTranscript(recentChronological, latestAssistantText);
        if (transcript.isBlank()) {
            return;
        }
        String userBlock = truncate("Últimas mensagens:\n" + transcript.strip(), maxTranscriptChars);
        try {
            AICompletionRequest request =
                    new AICompletionRequest(
                            tenantId,
                            List.of(),
                            List.of(),
                            userBlock,
                            CLASSIFICATION_SYSTEM_PROMPT.strip(),
                            AiChatProvider.GEMINI);
            AICompletionResponse response = aiEngine.complete(request);
            String raw = response.content();
            ChatClassification parsed = parseClassification(raw);
            chatAnalyticsRepository.upsert(tenantId, phone, parsed.intent(), parsed.sentiment());
            leadScoringService.recalculateAndPersist(tenantId, phone);
            crmLeadIntentUpdater.onMainIntentClassified(tenantId, phone, parsed.intent(), Instant.now());
        } catch (RuntimeException e) {
            LOG.warn(
                    "chat analytics: falha ao classificar tenant={} phone={}: {}",
                    tenantId.value(),
                    phone,
                    e.toString());
        }
    }

    static String buildTranscript(List<ChatMessage> chronological, String latestAssistantText) {
        if (chronological == null || chronological.isEmpty()) {
            return appendAssistantLine("", latestAssistantText);
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : chronological) {
            String roleLabel = m.role() == ChatMessageRole.USER ? "CLIENTE" : "ASSISTENTE";
            String text = m.content() == null ? "" : m.content().replace('\n', ' ').strip();
            if (text.length() > 800) {
                text = text.substring(0, 800) + "…";
            }
            if (!text.isBlank()) {
                sb.append(roleLabel).append(": ").append(text).append('\n');
            }
        }
        return appendAssistantLine(sb.toString().strip(), latestAssistantText);
    }

    private static String appendAssistantLine(String base, String latestAssistantText) {
        if (latestAssistantText == null || latestAssistantText.isBlank()) {
            return base == null ? "" : base;
        }
        String t = latestAssistantText.replace('\n', ' ').strip();
        if (t.length() > 800) {
            t = t.substring(0, 800) + "…";
        }
        if (base == null || base.isBlank()) {
            return "ASSISTENTE: " + t;
        }
        return base + "\nASSISTENTE: " + t;
    }

    public record ChatClassification(ChatMainIntent intent, ChatSentiment sentiment) {}

    /** Expõe parsing para testes e integrações sem chamar o modelo. */
    public static ChatClassification parseClassification(String rawModelOutput) {
        if (rawModelOutput == null || rawModelOutput.isBlank()) {
            return new ChatClassification(ChatMainIntent.Outros, ChatSentiment.Neutro);
        }
        String intentLine = null;
        String sentimentLine = null;
        for (String line : rawModelOutput.replace("\r\n", "\n").split("\n")) {
            String s = line.strip();
            if (s.toUpperCase(Locale.ROOT).startsWith("INTENCAO:")) {
                intentLine = s.substring(s.indexOf(':') + 1).strip();
            } else if (s.toUpperCase(Locale.ROOT).startsWith("SENTIMENTO:")) {
                sentimentLine = s.substring(s.indexOf(':') + 1).strip();
            }
        }
        return new ChatClassification(
                parseMainIntent(intentLine != null ? intentLine : rawModelOutput),
                parseSentiment(sentimentLine != null ? sentimentLine : rawModelOutput));
    }

    public static ChatMainIntent parseMainIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatMainIntent.Outros;
        }
        String n = stripAccents(raw.toLowerCase(Locale.ROOT));
        if (matchesAny(n, "orcamento", "orc", "cotacao", "cotação", "preco", "preço", "valor")) {
            return ChatMainIntent.Orcamento;
        }
        if (matchesAny(n, "agendamento", "agendar", "horario", "horário", "consulta", "marcar")) {
            return ChatMainIntent.Agendamento;
        }
        if (matchesAny(n, "suporte", "ajuda", "duvida", "dúvida", "tecnico", "técnico", "problema")) {
            return ChatMainIntent.Suporte;
        }
        if (matchesAny(n, "venda", "comprar", "pedido", "catalogo", "catálogo")) {
            return ChatMainIntent.Venda;
        }
        if (matchesAny(n, "outros", "outro")) {
            return ChatMainIntent.Outros;
        }
        for (ChatMainIntent v : ChatMainIntent.values()) {
            if (n.contains(stripAccents(v.dbValue().toLowerCase(Locale.ROOT)))) {
                return v;
            }
        }
        return ChatMainIntent.Outros;
    }

    public static ChatSentiment parseSentiment(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatSentiment.Neutro;
        }
        String n = stripAccents(raw.toLowerCase(Locale.ROOT));
        if (matchesAny(n, "positivo", "positive")) {
            return ChatSentiment.Positivo;
        }
        if (matchesAny(n, "negativo", "negative")) {
            return ChatSentiment.Negativo;
        }
        if (matchesAny(n, "neutro", "neutral")) {
            return ChatSentiment.Neutro;
        }
        return ChatSentiment.Neutro;
    }

    private static String stripAccents(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static boolean matchesAny(String normalized, String... tokens) {
        for (String t : tokens) {
            String x = stripAccents(t.toLowerCase(Locale.ROOT));
            if (normalized.contains(x)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}
