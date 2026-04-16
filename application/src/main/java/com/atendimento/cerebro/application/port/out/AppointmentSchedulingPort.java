package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;

/**
 * Agendamento via Google Calendar (ou mock). Retorno em texto para o modelo.
 */
public interface AppointmentSchedulingPort {

    /**
     * @param isoDate data {@code yyyy-MM-dd} no fuso configurado em {@code cerebro.google.calendar.zone}.
     */
    String checkAvailability(TenantId tenantId, String isoDate);

    /**
     * @param isoDate {@code yyyy-MM-dd}
     * @param localTime {@code HH:mm} (24h)
     *     <p>Implementações rejeitam datas civis estritamente anteriores a "hoje" no fuso do calendário.
     */
    String createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId);

    /**
     * Remove o evento no calendário externo. Erros fatais (ex.: I/O) devem propagar para não marcar cancelamento na base.
     *
     * @return {@code true} se o evento foi removido ou ignorado de forma segura (ex.: id mock); {@code false} se não foi
     *     possível sincronizar (ex.: {@code googleEventId} vazio ou tenant sem calendário configurado na implementação
     *     Google).
     */
    boolean deleteCalendarEvent(TenantId tenantId, String googleEventId);
}
