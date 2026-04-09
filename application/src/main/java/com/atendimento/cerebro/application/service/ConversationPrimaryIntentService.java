package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.analytics.AnalyticsIntentTrigger;
import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.analytics.StaleConversationRow;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifica a <strong>intenção principal</strong> da conversa com base no histórico completo (Gemini),
 * após um limiar de mensagens ou por inatividade (conversa encerrada).
 */
public class ConversationPrimaryIntentService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationPrimaryIntentService.class);

    public static final String TRANSCRIPT_SYSTEM_PROMPT =
            """
            Analise a transcrição abaixo (mensagens na ordem cronológica). \
            Responda com EXATAMENTE uma linha no formato CATEGORIA|SENTIMENTO (sem espaços extras, sem outro texto):

            CATEGORIA (uma palavra em MAIÚSCULAS):
            ORCAMENTO — pedido de preço, orçamento, cotação, valores
            AGENDAMENTO — marcar horário, reunião, visita, agendamento
            DUVIDA_TECNICA — dúvida técnica, como usar, especificações, funcionamento
            RECLAMACAO — insatisfação, problema grave, devolução, reclamação
            OUTRO — não encaixa claramente nas anteriores

            SENTIMENTO (uma palavra em MAIÚSCULAS):
            POSITIVO — cliente satisfeito, cordial, agradecimento, tom favorável
            NEUTRO — tom neutro, apenas informação, ou misto
            NEGATIVO — irritação, insatisfação forte, urgência negativa

            Exemplo válido: ORCAMENTO|POSITIVO""";

    private final ChatMessageRepository chatMessageRepository;
    private final AnalyticsIntentsRepository analyticsIntentsRepository;
    private final AIEnginePort aiEngine;
    private final boolean enabled;
    private final int messageThreshold;
    private final int sessionLookbackDays;
    private final int maxTranscriptMessages;
    private final int maxTranscriptChars;
    private final boolean inactivityClassificationEnabled;
    private final int inactivityMinutes;
    private final int maxClassificationsPerRun;

    public ConversationPrimaryIntentService(
            ChatMessageRepository chatMessageRepository,
            AnalyticsIntentsRepository analyticsIntentsRepository,
            AIEnginePort aiEngine,
            boolean enabled,
            int messageThreshold,
            int sessionLookbackDays,
            int maxTranscriptMessages,
            int maxTranscriptChars,
            boolean inactivityClassificationEnabled,
            int inactivityMinutes,
            int maxClassificationsPerRun) {
        this.chatMessageRepository = chatMessageRepository;
        this.analyticsIntentsRepository = analyticsIntentsRepository;
        this.aiEngine = aiEngine;
        this.enabled = enabled;
        this.messageThreshold = Math.max(messageThreshold, 1);
        this.sessionLookbackDays = Math.min(Math.max(sessionLookbackDays, 1), 365);
        this.maxTranscriptMessages = Math.min(Math.max(maxTranscriptMessages, 4), 200);
        this.maxTranscriptChars = Math.min(Math.max(maxTranscriptChars, 1024), 32_000);
        this.inactivityClassificationEnabled = inactivityClassificationEnabled;
        this.inactivityMinutes = Math.max(inactivityMinutes, 5);
        this.maxClassificationsPerRun = Math.min(Math.max(maxClassificationsPerRun, 1), 10_000);
    }

    /** Após um turno WhatsApp concluído (resposta enviada / persistida). Invocado em thread de fundo. */
    public void handleTurnCompleted(TenantId tenantId, String phoneNumber) {
        if (!enabled || tenantId == null || phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String phone = phoneNumber.strip();
        Instant windowStart = Instant.now().minus(sessionLookbackDays, ChronoUnit.DAYS);
        Instant windowEnd = Instant.now();
        long total;
        try {
            total = chatMessageRepository.countMessagesForTenantPhoneSince(tenantId, phone, windowStart);
        } catch (RuntimeException e) {
            LOG.warn(
                    "primary intent: falha ao contar mensagens tenant={} phone={}: {}",
                    tenantId.value(),
                    phone,
                    e.toString());
            return;
        }
        if (total <= 0 || total % messageThreshold != 0) {
            return;
        }
        int turnCount = (int) Math.min(total, Integer.MAX_VALUE);
        try {
            if (analyticsIntentsRepository.existsMessageThresholdClassification(tenantId, phone, turnCount)) {
                return;
            }
            classifyAndPersist(
                    tenantId,
                    phone,
                    AnalyticsIntentTrigger.MESSAGE_THRESHOLD,
                    turnCount,
                    null,
                    windowStart,
                    windowEnd);
        } catch (RuntimeException e) {
            LOG.warn(
                    "primary intent (threshold) falhou tenant={} phone={}: {}",
                    tenantId.value(),
                    phone,
                    e.toString());
        }
    }

    /** Job: conversas inactivas (última mensagem há &gt; N minutos). */
    public void classifyStaleConversations() {
        if (!enabled || !inactivityClassificationEnabled) {
            return;
        }
        Instant now = Instant.now();
        Instant idleBefore = now.minus(inactivityMinutes, ChronoUnit.MINUTES);
        Instant oldest = now.minus(sessionLookbackDays, ChronoUnit.DAYS);
        List<StaleConversationRow> stale;
        try {
            stale = chatMessageRepository.findStaleConversations(idleBefore, oldest);
        } catch (RuntimeException e) {
            LOG.warn("primary intent inactivity: falha na consulta: {}", e.toString());
            return;
        }
        int done = 0;
        for (StaleConversationRow row : stale) {
            if (done >= maxClassificationsPerRun) {
                LOG.info(
                        "primary intent inactivity: limite maxClassificationsPerRun={} atingido",
                        maxClassificationsPerRun);
                break;
            }
            try {
                if (analyticsIntentsRepository.existsInactivityClassification(
                        row.tenantId(), row.phoneNumber(), row.lastActivityAt())) {
                    continue;
                }
                Instant windowStart = Instant.now().minus(sessionLookbackDays, ChronoUnit.DAYS);
                Instant end = row.lastActivityAt();
                long n =
                        chatMessageRepository.countMessagesForTenantPhoneSince(
                                row.tenantId(), row.phoneNumber(), windowStart);
                int turnCount = (int) Math.min(n, Integer.MAX_VALUE);
                classifyAndPersist(
                        row.tenantId(),
                        row.phoneNumber(),
                        AnalyticsIntentTrigger.INACTIVITY_CLOSE,
                        turnCount,
                        row.lastActivityAt(),
                        windowStart,
                        end);
                done++;
            } catch (RuntimeException e) {
                LOG.warn(
                        "primary intent (inactividade) falhou tenant={} phone={}: {}",
                        row.tenantId().value(),
                        row.phoneNumber(),
                        e.toString());
            }
        }
        if (done > 0) {
            LOG.info("primary intent inactivity: classificações gravadas={} candidatos={}", done, stale.size());
        }
    }

    private void classifyAndPersist(
            TenantId tenantId,
            String phone,
            AnalyticsIntentTrigger trigger,
            int turnCount,
            Instant conversationEndAt,
            Instant windowStart,
            Instant windowEnd) {
        List<ChatMessage> msgs =
                chatMessageRepository.findRecentMessagesChronological(
                        tenantId, phone, windowStart, windowEnd, maxTranscriptMessages);
        String transcript = buildTranscript(msgs);
        if (transcript.isBlank()) {
            return;
        }
        transcript = truncate(transcript.strip(), maxTranscriptChars);
        var request =
                new AICompletionRequest(
                        tenantId,
                        List.of(),
                        List.of(),
                        transcript,
                        TRANSCRIPT_SYSTEM_PROMPT.strip(),
                        AiChatProvider.GEMINI);
        String rawLabel = aiEngine.complete(request).content().strip();
        PrimaryIntentCategory category = parsePrimaryIntent(rawLabel);
        ConversationSentiment sentiment = parseConversationSentiment(rawLabel);
        analyticsIntentsRepository.insert(
                tenantId, phone, category, sentiment, trigger, turnCount, conversationEndAt, rawLabel);
    }

    static String buildTranscript(List<ChatMessage> chronological) {
        if (chronological == null || chronological.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : chronological) {
            String roleLabel =
                    m.role() == ChatMessageRole.USER ? "CLIENTE" : "ASSISTENTE";
            String text = m.content() == null ? "" : m.content().replace('\n', ' ').strip();
            if (text.length() > 800) {
                text = text.substring(0, 800) + "…";
            }
            if (!text.isBlank()) {
                sb.append(roleLabel).append(": ").append(text).append('\n');
            }
        }
        return sb.toString().strip();
    }

    static PrimaryIntentCategory parsePrimaryIntent(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return PrimaryIntentCategory.OUTRO;
        }
        String intentSegment = rawLabel.strip();
        int pipe = intentSegment.indexOf('|');
        if (pipe >= 0) {
            intentSegment = intentSegment.substring(0, pipe).strip();
        }
        String t = intentSegment.toUpperCase(Locale.ROOT);
        for (PrimaryIntentCategory c : PrimaryIntentCategory.values()) {
            if (t.contains(c.name())) {
                return c;
            }
        }
        return PrimaryIntentCategory.OUTRO;
    }

    /** Extrai POSITIVO, NEUTRO ou NEGATIVO da resposta {@code CATEGORIA|SENTIMENTO}; legacy sem pipe → NEUTRO. */
    static ConversationSentiment parseConversationSentiment(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return ConversationSentiment.NEUTRO;
        }
        String s = rawLabel.strip();
        int pipe = s.indexOf('|');
        if (pipe < 0 || pipe >= s.length() - 1) {
            return ConversationSentiment.NEUTRO;
        }
        String part = s.substring(pipe + 1).strip().toUpperCase(Locale.ROOT);
        if (part.contains(ConversationSentiment.NEGATIVO.name())) {
            return ConversationSentiment.NEGATIVO;
        }
        if (part.contains(ConversationSentiment.POSITIVO.name())) {
            return ConversationSentiment.POSITIVO;
        }
        if (part.contains(ConversationSentiment.NEUTRO.name())) {
            return ConversationSentiment.NEUTRO;
        }
        try {
            return ConversationSentiment.valueOf(part.split("\\s+")[0]);
        } catch (IllegalArgumentException e) {
            return ConversationSentiment.NEUTRO;
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}
