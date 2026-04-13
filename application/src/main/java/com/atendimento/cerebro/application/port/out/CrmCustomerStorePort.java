package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CrmCustomerStorePort {

    /**
     * Garante linha CRM na primeira mensagem / sessão; não incrementa agendamentos.
     *
     * @param displayNameOptional nome de contacto WhatsApp, se existir
     */
    void ensureOnConversationStart(TenantId tenantId, String conversationId, Optional<String> displayNameOptional);

    /** Após {@code tenant_appointments} gravado com sucesso. Ignora se {@code conversationId} vazio. */
    void recordSuccessfulAppointment(TenantId tenantId, String conversationId, String clientName);

    void updateInternalNotes(TenantId tenantId, UUID customerId, String internalNotes);

    /**
     * Regista intenção Orçamento/Agendamento (analytics) para {@code conversation_id} WhatsApp.
     * Garante linha CRM e define estado OPEN (ou mantém ASSIGNED/DISMISSED).
     */
    void applyLeadIntentFromClassification(
            TenantId tenantId, String conversationId, ChatMainIntent intent, Instant classifiedAt);

    /**
     * Marca oportunidade como assumida pelo dono (PENDING_LEAD ou HOT_LEAD).
     *
     * @return true se uma linha foi actualizada
     */
    boolean assumePendingLead(TenantId tenantId, UUID customerId);

    /**
     * Orçamento/Agendamento há &gt; 1 h sem agendamento após a deteção → {@code HOT_LEAD}.
     */
    int markStaleBudgetSchedulingAsHotLeadWithoutAppointment();

    /** Promove leads OPEN cuja janela de intenção excedeu o limiar para PENDING_LEAD. */
    int promoteStaleOpenLeadsToPending(Duration window);

    /** Atualiza apenas {@code lead_score} (scoring dinâmico pós-{@code chat_analytics}). */
    void updateLeadScore(TenantId tenantId, String conversationId, int leadScore);
}
