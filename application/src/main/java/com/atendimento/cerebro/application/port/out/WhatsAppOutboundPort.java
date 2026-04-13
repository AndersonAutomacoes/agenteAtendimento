package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

/**
 * Envio de mensagens de saída para o WhatsApp (Meta, Evolution ou simulação por log), via rotas Camel.
 */
public interface WhatsAppOutboundPort {

    void sendMessage(TenantId tenantId, String to, String text);

    /**
     * @param whatsAppInteractive se presente e o tenant usar Evolution, pode ser enviado como mensagem com botões
     *     (horários); caso contrário usa-se só o texto.
     */
    void sendMessage(
            TenantId tenantId, String to, String text, Optional<WhatsAppInteractiveReply> whatsAppInteractive);

    /**
     * Reenvia texto já associado a uma linha ASSISTANT existente ({@code chat_message.id}), sem novo INSERT.
     */
    void sendMessage(TenantId tenantId, String to, String text, long existingAssistantMessageId);
}
