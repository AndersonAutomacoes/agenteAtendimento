package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.AppointmentReminderCandidate;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * Verdadeiro se existir agendamento local que se sobreponha ao intervalo {@code [startInclusive, endExclusive)}
     * (evita duplicidade no modo mock / base {@code tenant_appointments}).
     */
    boolean existsOverlapping(TenantId tenantId, Instant startInclusive, Instant endExclusive);

    /**
     * Agendamento activo ({@code cancelled_at} nulo) no dia civil de {@code starts_at} no fuso dado; se vários, o de
     * {@code starts_at} mais recente.
     */
    Optional<TenantAppointmentListItem> findActiveByConversationAndLocalDate(
            TenantId tenantId, String conversationId, LocalDate day, String zoneId);

    /**
     * Último agendamento já cancelado nesse dia civil e conversa (para idempotência de cancelamento).
     */
    Optional<TenantAppointmentListItem> findCancelledByConversationAndLocalDate(
            TenantId tenantId, String conversationId, LocalDate day, String zoneId);

    /** Agendamentos com {@code booking_status = AGENDADO} para a conversa, início crescente (numeração estável). */
    List<TenantAppointmentListItem> listAgendadoByConversationOrderedAscending(
            TenantId tenantId, String conversationId, String zoneId);

    Optional<TenantAppointmentListItem> findByIdForTenantAndConversation(
            TenantId tenantId, long appointmentId, String conversationId, String zoneId);

    /**
     * Agendamentos {@code AGENDADO} com início no dia civil {@code localDay} no fuso {@code zoneId}, ainda sem lembrete
     * de véspera enviado.
     */
    List<AppointmentReminderCandidate> listAgendadoForReminderOnLocalDate(LocalDate localDay, String zoneId);
}
