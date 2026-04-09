package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

/** Classificação curta da mensagem do utilizador (etiqueta única) para monitorização. */
public interface IntentDetectionPort {

    Optional<String> detectIntent(TenantId tenantId, String userMessageText);
}
