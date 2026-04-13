package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.StaleConversationRow;
import com.atendimento.cerebro.application.analytics.TenantPhonePair;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
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
public class JdbcChatMessageRepository implements ChatMessageRepository {

    private final JdbcClient jdbcClient;

    public JdbcChatMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void save(ChatMessage message) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO chat_message
                            (tenant_id, phone_number, role, content, status, occurred_at, contact_display_name, contact_profile_pic_url, detected_intent)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(message.tenantId().value())
                .param(message.phoneNumber())
                .param(message.role().name())
                .param(message.content())
                .param(message.status().name())
                .param(Timestamp.from(message.timestamp()))
                .param(message.contactDisplayName())
                .param(message.contactProfilePicUrl())
                .param(message.detectedIntent())
                .update();
    }

    @Override
    @Transactional
    public long insertReturningId(ChatMessage message) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO chat_message
                            (tenant_id, phone_number, role, content, status, occurred_at, contact_display_name, contact_profile_pic_url, detected_intent)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """)
                .param(message.tenantId().value())
                .param(message.phoneNumber())
                .param(message.role().name())
                .param(message.content())
                .param(message.status().name())
                .param(Timestamp.from(message.timestamp()))
                .param(message.contactDisplayName())
                .param(message.contactProfilePicUrl())
                .param(message.detectedIntent())
                .query(Long.class)
                .single();
    }

    @Override
    @Transactional
    public void updateStatus(long id, ChatMessageStatus status) {
        int n =
                jdbcClient
                        .sql("UPDATE chat_message SET status = ? WHERE id = ?")
                        .param(status.name())
                        .param(id)
                        .update();
        if (n != 1) {
            throw new IllegalStateException("chat_message updateStatus: expected 1 row, got " + n + " for id=" + id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatMessage> findByIdAndTenant(long id, TenantId tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, phone_number, role, content, status, occurred_at, contact_display_name, contact_profile_pic_url, detected_intent
                        FROM chat_message
                        WHERE id = ? AND tenant_id = ?
                        """)
                .param(id)
                .param(tenantId.value())
                .query(JdbcChatMessageRepository::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findLastByTenantId(TenantId tenantId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        String tenant = tenantId.value();
        /*
         * PostgreSQL: LIMIT ? como segundo placeholder costuma falhar com
         * "could not determine data type of parameter" em prepared statements.
         * O limite é inteiro limitado localmente (1–500), logo é seguro interpolar.
         */
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, phone_number, role, content, status, occurred_at, contact_display_name, contact_profile_pic_url, detected_intent
                        FROM chat_message
                        WHERE tenant_id = ?
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT """
                                + " "
                                + safeLimit)
                .param(tenant)
                .query(JdbcChatMessageRepository::mapRow)
                .list();
    }

    @Override
    @Transactional
    public int updateDetectedIntentForLatestUser(TenantId tenantId, String phoneNumber, String detectedIntent) {
        if (detectedIntent == null || detectedIntent.length() > 128) {
            throw new IllegalArgumentException("detectedIntent must be non-null and at most 128 characters");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return 0;
        }
        return jdbcClient
                .sql(
                        """
                        UPDATE chat_message
                        SET detected_intent = ?
                        WHERE id = (
                            SELECT id FROM chat_message
                            WHERE tenant_id = ? AND phone_number = ? AND role = 'USER'
                            ORDER BY occurred_at DESC, id DESC
                            LIMIT 1
                        )
                        """)
                .param(detectedIntent.strip())
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findRecentForTenantAndPhone(
            TenantId tenantId, String phoneNumber, Instant notBefore, int limit) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 64);
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, phone_number, role, content, status, occurred_at, contact_display_name, contact_profile_pic_url, detected_intent
                        FROM chat_message
                        WHERE tenant_id = ? AND phone_number = ? AND occurred_at >= ?
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT """
                                + " "
                                + safeLimit)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .param(Timestamp.from(notBefore))
                .query(JdbcChatMessageRepository::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantPhonePair> findDistinctTenantPhonesWithUserMessages(
            Instant startInclusive, Instant endExclusive) {
        return jdbcClient
                .sql(
                        """
                        SELECT DISTINCT tenant_id, phone_number
                        FROM chat_message
                        WHERE role = 'USER'
                          AND occurred_at >= ?
                          AND occurred_at < ?
                        ORDER BY tenant_id, phone_number
                        """)
                .param(Timestamp.from(startInclusive))
                .param(Timestamp.from(endExclusive))
                .query(
                        (rs, rowNum) ->
                                new TenantPhonePair(
                                        new TenantId(rs.getString("tenant_id")),
                                        rs.getString("phone_number")))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public String aggregateUserMessageTextForRange(
            TenantId tenantId, String phoneNumber, Instant startInclusive, Instant endExclusive) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        return jdbcClient
                .sql(
                        """
                        SELECT COALESCE(string_agg(content, E'\\n' ORDER BY occurred_at ASC, id ASC), '')
                        FROM chat_message
                        WHERE tenant_id = ? AND phone_number = ? AND role = 'USER'
                          AND occurred_at >= ?
                          AND occurred_at < ?
                        """)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .param(Timestamp.from(startInclusive))
                .param(Timestamp.from(endExclusive))
                .query(String.class)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public long countMessagesForTenantPhoneSince(TenantId tenantId, String phoneNumber, Instant notBeforeInclusive) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return 0L;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM chat_message
                                WHERE tenant_id = ? AND phone_number = ? AND occurred_at >= ?
                                """)
                        .param(tenantId.value())
                        .param(phoneNumber.strip())
                        .param(Timestamp.from(notBeforeInclusive))
                        .query(Long.class)
                        .single();
        return n != null ? n : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUserMessagesStrictlyAfter(TenantId tenantId, String phoneNumber, Instant afterExclusive) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return 0L;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM chat_message
                                WHERE tenant_id = ? AND phone_number = ? AND role = 'USER' AND occurred_at > ?
                                """)
                        .param(tenantId.value())
                        .param(phoneNumber.strip())
                        .param(Timestamp.from(afterExclusive))
                        .query(Long.class)
                        .single();
        return n != null ? n : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUserAssistantExchangesStrictlyAfter(
            TenantId tenantId, String phoneNumber, Instant afterExclusive) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return 0L;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                WITH w AS (
                                    SELECT role,
                                           LAG(role) OVER (ORDER BY occurred_at ASC, id ASC) AS prev_role
                                    FROM chat_message
                                    WHERE tenant_id = ? AND phone_number = ? AND occurred_at > ?
                                )
                                SELECT COUNT(*)::bigint FROM w
                                WHERE role = 'ASSISTANT' AND prev_role = 'USER'
                                """)
                        .param(tenantId.value())
                        .param(phoneNumber.strip())
                        .param(Timestamp.from(afterExclusive))
                        .query(Long.class)
                        .single();
        return n != null ? n : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> findRecentMessagesChronological(
            TenantId tenantId,
            String phoneNumber,
            Instant fromInclusive,
            Instant toInclusive,
            int maxMessages) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return List.of();
        }
        int safeMax = Math.min(Math.max(maxMessages, 1), 200);
        String tenant = tenantId.value();
        String phone = phoneNumber.strip();
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, phone_number, role, content, status, occurred_at,
                               contact_display_name, contact_profile_pic_url, detected_intent
                        FROM (
                            SELECT id, tenant_id, phone_number, role, content, status, occurred_at,
                                   contact_display_name, contact_profile_pic_url, detected_intent
                            FROM chat_message
                            WHERE tenant_id = ? AND phone_number = ?
                              AND occurred_at >= ? AND occurred_at <= ?
                            ORDER BY occurred_at DESC, id DESC
                            LIMIT """
                                + " "
                                + safeMax
                                + """
                        ) sub
                        ORDER BY sub.occurred_at ASC, sub.id ASC
                        """)
                .param(tenant)
                .param(phone)
                .param(Timestamp.from(fromInclusive))
                .param(Timestamp.from(toInclusive))
                .query(JdbcChatMessageRepository::mapRow)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaleConversationRow> findStaleConversations(Instant idleBefore, Instant oldestLastActivity) {
        return jdbcClient
                .sql(
                        """
                        SELECT tenant_id, phone_number, MAX(occurred_at) AS last_at
                        FROM chat_message
                        GROUP BY tenant_id, phone_number
                        HAVING MAX(occurred_at) <= ? AND MAX(occurred_at) >= ?
                        ORDER BY tenant_id, phone_number
                        """)
                .param(Timestamp.from(idleBefore))
                .param(Timestamp.from(oldestLastActivity))
                .query(
                        (rs, rowNum) -> {
                            Timestamp ts = rs.getTimestamp("last_at");
                            Instant at = ts != null ? ts.toInstant() : Instant.EPOCH;
                            return new StaleConversationRow(
                                    new TenantId(rs.getString("tenant_id")),
                                    rs.getString("phone_number"),
                                    at);
                        })
                .list();
    }

    private static ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("occurred_at");
        Instant at = ts != null ? ts.toInstant() : Instant.now();
        return new ChatMessage(
                rs.getLong("id"),
                new TenantId(rs.getString("tenant_id")),
                rs.getString("phone_number"),
                ChatMessageRole.valueOf(rs.getString("role")),
                rs.getString("content"),
                ChatMessageStatus.valueOf(rs.getString("status")),
                at,
                rs.getString("contact_display_name"),
                rs.getString("contact_profile_pic_url"),
                rs.getString("detected_intent"));
    }
}
