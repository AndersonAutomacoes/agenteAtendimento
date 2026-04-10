package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Collection;
import java.util.Map;

/**
 * Estado por contacto WhatsApp: quando {@code is_bot_enabled} é falso, o modelo não responde.
 * Ausência de linha equivale a {@code is_bot_enabled == true}.
 */
public interface ConversationBotStatePort {

    boolean isBotEnabled(TenantId tenantId, String phoneNumber);

    void setBotEnabled(TenantId tenantId, String phoneNumber, boolean enabled);

    /**
     * Reativa o bot e marca a próxima resposta Gemini para receber instrução de retomada após atendimento humano.
     */
    void enableBotWithResumeContextHint(TenantId tenantId, String phoneNumber);

    /**
     * Se existir indicação pendente, limpa-a e devolve {@code true} (uma só vez por retomada).
     */
    boolean consumeResumeAiContextIfPending(TenantId tenantId, String phoneNumber);

    /**
     * Resolve o estado efectivo para cada telefone (ausência de linha → {@code true}).
     */
    Map<String, Boolean> resolveBotEnabledForPhones(TenantId tenantId, Collection<String> phoneNumbers);
}
