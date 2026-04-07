package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresConversationContextStore implements ConversationContextStorePort {

    private final JdbcClient jdbcClient;

    public PostgresConversationContextStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationContext> load(TenantId tenantId, ConversationId conversationId) {
        String tenant = tenantId.value();
        String session = conversationId.value();

        List<Message> messages = jdbcClient
                .sql(
                        """
                        SELECT role, content, occurred_at
                        FROM conversation_message
                        WHERE tenant_id = ?
                          AND conversation_id = ?
                        ORDER BY occurred_at ASC, id ASC
                        """)
                .param(tenant)
                .param(session)
                .query((rs, rowNum) -> {
                    MessageRole role = MessageRole.valueOf(rs.getString("role"));
                    String content = rs.getString("content");
                    Instant at = readOccurredAt(rs);
                    return new Message(role, content, at);
                })
                .list();

        if (messages.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ConversationContext.builder()
                .tenantId(tenantId)
                .conversationId(conversationId)
                .messages(messages)
                .build());
    }

    @Override
    @Transactional
    public void save(ConversationContext context) {
        String tenant = context.getTenantId().value();
        String session = context.getConversationId().value();

        jdbcClient
                .sql("DELETE FROM conversation_message WHERE tenant_id = ? AND conversation_id = ?")
                .param(tenant)
                .param(session)
                .update();

        for (Message message : context.getMessages()) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO conversation_message
                                (tenant_id, conversation_id, role, content, occurred_at)
                            VALUES (?, ?, ?, ?, ?)
                            """)
                    .param(tenant)
                    .param(session)
                    .param(message.role().name())
                    .param(message.content())
                    .param(Timestamp.from(message.timestamp()))
                    .update();
        }
    }

    /**
     * {@code getObject(..., Instant.class)} não é suportado de forma confiável para {@code TIMESTAMPTZ} no driver
     * PostgreSQL; {@link Timestamp#toInstant()} evita o erro de conversão em runtime.
     */
    private static Instant readOccurredAt(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("occurred_at");
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
