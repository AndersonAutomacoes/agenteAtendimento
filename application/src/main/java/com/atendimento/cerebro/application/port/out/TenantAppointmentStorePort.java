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
}
