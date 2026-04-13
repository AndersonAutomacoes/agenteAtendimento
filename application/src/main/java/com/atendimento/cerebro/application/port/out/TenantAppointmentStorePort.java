package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;

public interface TenantAppointmentStorePort {

    void insert(TenantAppointmentRecord record);
}
