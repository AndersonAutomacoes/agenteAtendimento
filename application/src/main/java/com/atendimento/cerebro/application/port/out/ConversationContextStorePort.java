package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

public interface ConversationContextStorePort {

    Optional<ConversationContext> load(TenantId tenantId, ConversationId conversationId);

    void save(ConversationContext context);
}
