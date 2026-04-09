package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.analytics.ConversationAnalyticsCategory;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.AnalyticsStatsRepository;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifica conversas (tenant + telefone) num intervalo horário e persiste em {@code analytics_stats}.
 */
public class ConversationCategoryAnalyticsService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversationCategoryAnalyticsService.class);

    static final String CLASSIFICATION_SYSTEM_PROMPT =
            """
            Classifique a intenção principal do cliente numa única palavra-chave em MAIÚSCULAS.
            Valores permitidos (responda só com uma destas, sem pontuação extra): VENDAS, SUPORTE, FINANCEIRO.
            Se não tiver informação suficiente ou não encaixar, responda OUTRO.
            """;

    private final ChatMessageRepository chatMessageRepository;
    private final AnalyticsStatsRepository analyticsStatsRepository;
    private final AIEnginePort aiEngine;
    private final int maxConversationsPerRun;
    private final int maxUserTextChars;

    public ConversationCategoryAnalyticsService(
            ChatMessageRepository chatMessageRepository,
            AnalyticsStatsRepository analyticsStatsRepository,
            AIEnginePort aiEngine,
            int maxConversationsPerRun,
            int maxUserTextChars) {
        this.chatMessageRepository = chatMessageRepository;
        this.analyticsStatsRepository = analyticsStatsRepository;
        this.aiEngine = aiEngine;
        this.maxConversationsPerRun = Math.min(Math.max(maxConversationsPerRun, 1), 10_000);
        this.maxUserTextChars = Math.min(Math.max(maxUserTextChars, 256), 32_000);
    }

    /**
     * Classifica pares com mensagens USER em {@code [windowStartUtc, windowEndUtc)}; idempotente por
     * {@code (tenant, bucket_hour = windowStartUtc, phone)}.
     */
    public void classifyForWindow(Instant windowStartUtc, Instant windowEndUtc) {
        var pairs =
                chatMessageRepository.findDistinctTenantPhonesWithUserMessages(
                        windowStartUtc, windowEndUtc);
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (var pair : pairs) {
            if (processed >= maxConversationsPerRun) {
                LOG.info(
                        "Analytics categorization: limite maxConversationsPerRun={} atingido; restante não processado nesta execução",
                        maxConversationsPerRun);
                break;
            }
            try {
                if (analyticsStatsRepository.existsForBucketAndPhone(
                        pair.tenantId(), windowStartUtc, pair.phoneNumber())) {
                    skipped++;
                    continue;
                }
                String text =
                        chatMessageRepository.aggregateUserMessageTextForRange(
                                pair.tenantId(), pair.phoneNumber(), windowStartUtc, windowEndUtc);
                if (text == null || text.isBlank()) {
                    skipped++;
                    continue;
                }
                text = truncate(text.strip(), maxUserTextChars);
                var request =
                        new AICompletionRequest(
                                pair.tenantId(),
                                List.of(),
                                List.of(),
                                text,
                                CLASSIFICATION_SYSTEM_PROMPT.strip(),
                                AiChatProvider.GEMINI);
                String rawLabel = aiEngine.complete(request).content().strip();
                ConversationAnalyticsCategory category = parseCategory(rawLabel);
                boolean inserted =
                        analyticsStatsRepository.insertIfAbsent(
                                pair.tenantId(),
                                windowStartUtc,
                                pair.phoneNumber(),
                                category,
                                rawLabel);
                if (inserted) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                LOG.warn(
                        "Falha ao classificar analytics tenantId={} phone={}: {}",
                        pair.tenantId().value(),
                        pair.phoneNumber(),
                        e.toString());
            }
        }
        LOG.info(
                "Analytics categorization para janela [{}, {}): processadas={}, ignoradas={}, falhas={}, candidatos={}",
                windowStartUtc,
                windowEndUtc,
                processed,
                skipped,
                failed,
                pairs.size());
    }

    static ConversationAnalyticsCategory parseCategory(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return ConversationAnalyticsCategory.OUTRO;
        }
        String t = rawLabel.strip().toUpperCase(Locale.ROOT);
        for (ConversationAnalyticsCategory c : ConversationAnalyticsCategory.values()) {
            if (t.contains(c.name())) {
                return c;
            }
        }
        return ConversationAnalyticsCategory.OUTRO;
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}
