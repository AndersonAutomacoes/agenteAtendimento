package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.StaleConversationRow;
import com.atendimento.cerebro.application.analytics.TenantPhonePair;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {

    void save(ChatMessage message);

    /**
     * Insere uma mensagem e devolve o {@code id} gerado ({@code RETURNING id}).
     */
    long insertReturningId(ChatMessage message);

    void updateStatus(long id, ChatMessageStatus status);

    Optional<ChatMessage> findByIdAndTenant(long id, TenantId tenantId);

    List<ChatMessage> findLastByTenantId(TenantId tenantId, int limit);

    /**
     * Últimas mensagens do par tenant + telefone (número como gravado), desde {@code notBefore},
     * ordenadas da mais recente para a mais antiga.
     */
    List<ChatMessage> findRecentForTenantAndPhone(
            TenantId tenantId, String phoneNumber, Instant notBefore, int limit);

    /**
     * Atualiza {@code detected_intent} na linha USER mais recente do par tenant ↔ telefone.
     *
     * @return linhas atualizadas (0 ou 1)
     */
    int updateDetectedIntentForLatestUser(TenantId tenantId, String phoneNumber, String detectedIntent);

    /**
     * Pares distintos com pelo menos uma mensagem USER no intervalo
     * {@code [startInclusive, endExclusive)}.
     */
    List<TenantPhonePair> findDistinctTenantPhonesWithUserMessages(
            Instant startInclusive, Instant endExclusive);

    /**
     * Texto das mensagens USER do intervalo, por ordem cronológica, separadas por newline.
     * String vazia se não houver USER.
     */
    String aggregateUserMessageTextForRange(
            TenantId tenantId, String phoneNumber, Instant startInclusive, Instant endExclusive);

    /** Total de linhas em {@code chat_message} para o par tenant+telefone desde {@code notBefore} inclusive. */
    long countMessagesForTenantPhoneSince(TenantId tenantId, String phoneNumber, Instant notBeforeInclusive);

    /**
     * Até {@code maxMessages} mensagens mais recentes no intervalo, em ordem cronológica crescente
     * (para transcrição / classificação).
     */
    List<ChatMessage> findRecentMessagesChronological(
            TenantId tenantId,
            String phoneNumber,
            Instant fromInclusive,
            Instant toInclusive,
            int maxMessages);

    /**
     * Conversas cuja última mensagem está em {@code [oldestLastActivity, idleBefore]} (inatividade).
     */
    List<StaleConversationRow> findStaleConversations(Instant idleBefore, Instant oldestLastActivity);
}
