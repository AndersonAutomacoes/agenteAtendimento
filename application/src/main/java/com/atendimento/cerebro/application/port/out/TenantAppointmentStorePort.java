package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import java.time.Instant;

public interface TenantAppointmentStorePort {

    /** @return {@code id} (PK) da linha inserida */
    long insert(TenantAppointmentRecord record);

    /**
     * @return {@code true} se uma linha com {@code booking_status = AGENDADO} foi actualizada.
     */
    boolean markCancelled(long appointmentId, Instant cancelledAt);

    /**
     * Estado inicial do tracking de notificação de confirmação (após persistir o agendamento e antes do envio
     * assíncrono).
     */
    void markConfirmationNotificationPending(long appointmentId);

    /**
     * Marca a notificação de confirmação como entregue. {@code messageId} pode ser {@code null} (ex.: Meta ou simulado).
     *
     * @return {@code true} se uma linha existente foi actualizada.
     */
    boolean markAsNotified(long appointmentId, String messageId);

    /**
     * Marca lembrete de véspera como enviado ({@code reminder_sent = true}).
     *
     * @return {@code true} se uma linha {@code AGENDADO} com {@code reminder_sent = false} foi actualizada.
     */
    boolean markReminderSent(long appointmentId);
}
