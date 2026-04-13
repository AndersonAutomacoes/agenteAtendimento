package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.dto.ChatAnalyticsLeadSnapshot;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

/**
 * Pontuação de lead a partir de {@code chat_analytics.main_intent} (matriz de pesos) e trocas
 * USER→ASSISTANT em {@code chat_message} após a primeira classificação analytics.
 */
public final class LeadScoringService {

    public static final int ENGAGEMENT_POINTS_PER_EXCHANGE = 5;
    public static final int SCORE_CAP = 100;
    /** A partir de 2 trocas após a âncora, considera-se agendamento em curso (vs. só disponibilidade). */
    public static final int AGENDAMENTO_DEEP_EXCHANGES = 2;

    private final ChatAnalyticsRepository chatAnalyticsRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CrmCustomerStorePort crmCustomerStore;

    public LeadScoringService(
            ChatAnalyticsRepository chatAnalyticsRepository,
            ChatMessageRepository chatMessageRepository,
            CrmCustomerStorePort crmCustomerStore) {
        this.chatAnalyticsRepository = chatAnalyticsRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.crmCustomerStore = crmCustomerStore;
    }

    /**
     * Recalcula {@code crm_customer.lead_score} quando há linha em {@code chat_analytics} para o telefone.
     * Sem analytics, não altera o CRM.
     */
    public void recalculateAndPersist(TenantId tenantId, String phoneNumber) {
        if (tenantId == null || phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String phone = phoneNumber.strip();
        var snapshotOpt = chatAnalyticsRepository.findLeadSnapshot(tenantId, phone);
        if (snapshotOpt.isEmpty()) {
            return;
        }
        ChatAnalyticsLeadSnapshot snapshot = snapshotOpt.get();
        Instant anchor = snapshot.engagementAnchor() != null ? snapshot.engagementAnchor() : Instant.EPOCH;
        long exchanges = chatMessageRepository.countUserAssistantExchangesStrictlyAfter(tenantId, phone, anchor);
        int score = computeScore(snapshot.mainIntent(), exchanges);
        String conv = CrmConversationSupport.whatsAppConversationIdFromPhoneDigits(phone);
        if (conv == null) {
            return;
        }
        crmCustomerStore.updateLeadScore(tenantId, conv, score);
    }

    /**
     * Base por intenção (alinhado a {@code last_detected_intent} / {@code main_intent}) e profundidade para
     * Agendamento: disponibilidade (85) vs. fluxo em curso (95).
     */
    public static int basePoints(ChatMainIntent intent, long exchangesStrictlyAfterAnchor) {
        if (intent == null) {
            return 10;
        }
        return switch (intent) {
            case Agendamento ->
                    exchangesStrictlyAfterAnchor >= AGENDAMENTO_DEEP_EXCHANGES ? 95 : 85;
            case Orcamento -> 50;
            case Suporte, Outros -> 10;
            case Venda -> 35;
        };
    }

    public static int computeScore(ChatMainIntent intent, long userAssistantExchangesAfterAnchor) {
        long ex = Math.max(0, userAssistantExchangesAfterAnchor);
        int base = basePoints(intent, ex);
        long total = (long) base + ENGAGEMENT_POINTS_PER_EXCHANGE * ex;
        return (int) Math.min(SCORE_CAP, total);
    }
}
