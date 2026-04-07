package com.atendimento.cerebro.domain.conversation;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationContext {

    private final TenantId tenantId;
    private final ConversationId conversationId;
    private final List<Message> messages;

    public TenantId getTenantId() {
        return tenantId;
    }

    public ConversationId getConversationId() {
        return conversationId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    private ConversationContext(TenantId tenantId, ConversationId conversationId, List<Message> messages) {
        if (tenantId == null || conversationId == null) {
            throw new IllegalArgumentException("tenantId and conversationId are required");
        }
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ConversationContext append(Message first, Message... rest) {
        List<Message> next = new ArrayList<>(messages);
        next.add(first);
        for (Message m : rest) {
            next.add(m);
        }
        return new ConversationContext(tenantId, conversationId, Collections.unmodifiableList(next));
    }

    public static final class Builder {
        private TenantId tenantId;
        private ConversationId conversationId;
        private List<Message> messages = List.of();

        private Builder() {}

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder conversationId(ConversationId conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public ConversationContext build() {
            return new ConversationContext(tenantId, conversationId, messages);
        }
    }
}
