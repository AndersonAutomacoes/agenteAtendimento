package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

public interface WhatsAppTenantLookupPort {

    Optional<TenantId> findTenantIdByWhatsAppNumber(String normalizedPhoneDigits);
}
