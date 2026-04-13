package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TenantAppointmentQueryPort {

    enum ListScope {
        ALL,
        TODAY,
        FUTURE
    }

    List<TenantAppointmentListItem> list(TenantId tenantId, ListScope scope, String zoneId);

    /** Contagem de agendamentos com início em [fromInclusive, toExclusive). */
    long countStartsInRange(TenantId tenantId, Instant fromInclusive, Instant toExclusive);

    /**
     * Próximo agendamento futuro por contacto, chave = dígitos do telefone (como em {@code chat_message.phone_number}).
     */
    Map<String, TenantAppointmentListItem> findEarliestUpcomingByPhoneDigits(
            TenantId tenantId, List<String> phoneDigits, String zoneId);

    /** Agendamentos do contacto (mesmo {@code conversation_id} que no CRM), mais recentes primeiro. */
    List<TenantAppointmentListItem> listByConversationId(TenantId tenantId, String conversationId, String zoneId);

    /** Último agendamento por data de início (passado ou futuro), para contexto da IA. */
    Optional<TenantAppointmentListItem> findMostRecentByConversationId(
            TenantId tenantId, String conversationId, String zoneId);
}
