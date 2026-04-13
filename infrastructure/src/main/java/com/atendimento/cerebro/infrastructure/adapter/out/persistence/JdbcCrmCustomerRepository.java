package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcCrmCustomerRepository implements CrmCustomerStorePort, CrmCustomerQueryPort {

    private static final int INTERNAL_NOTES_MAX_CHARS = 16_000;

    private final JdbcClient jdbcClient;

    public JdbcCrmCustomerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void ensureOnConversationStart(TenantId tenantId, String conversationId, Optional<String> displayNameOptional) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String conv = conversationId.strip();
        String tid = tenantId.value();
        Optional<String> phoneOpt = CrmConversationSupport.phoneDigitsOnlyFromConversationId(conv);
        String phone = phoneOpt.orElse(null);
        String display = displayNameOptional.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);

        jdbcClient
                .sql(
                        """
                        INSERT INTO crm_customer
                            (tenant_id, conversation_id, phone_number, full_name, first_interaction, total_appointments)
                        VALUES (?, ?, ?, ?, NOW(), 0)
                        ON CONFLICT (tenant_id, conversation_id) DO UPDATE SET
                            full_name = CASE
                                WHEN (crm_customer.full_name IS NULL OR trim(crm_customer.full_name) = '')
                                    AND EXCLUDED.full_name IS NOT NULL AND trim(EXCLUDED.full_name) <> ''
                                THEN trim(EXCLUDED.full_name)
                                ELSE crm_customer.full_name
                            END,
                            phone_number = COALESCE(crm_customer.phone_number, EXCLUDED.phone_number),
                            updated_at = NOW()
                        """)
                .param(tid)
                .param(conv)
                .param(phone)
                .param(display)
                .update();
    }

    @Override
    @Transactional
    public void recordSuccessfulAppointment(TenantId tenantId, String conversationId, String clientName) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String conv = conversationId.strip();
        String tid = tenantId.value();
        Optional<String> phoneOpt = CrmConversationSupport.phoneDigitsOnlyFromConversationId(conv);
        String phone = phoneOpt.orElse(null);
        String name = clientName == null ? "" : clientName.strip();

        jdbcClient
                .sql(
                        """
                        INSERT INTO crm_customer
                            (tenant_id, conversation_id, phone_number, full_name, first_interaction, total_appointments)
                        VALUES (?, ?, ?, ?, NOW(), 1)
                        ON CONFLICT (tenant_id, conversation_id) DO UPDATE SET
                            total_appointments = crm_customer.total_appointments + 1,
                            full_name = CASE
                                WHEN EXCLUDED.full_name IS NOT NULL AND trim(EXCLUDED.full_name) <> ''
                                THEN trim(EXCLUDED.full_name)
                                ELSE crm_customer.full_name
                            END,
                            phone_number = COALESCE(crm_customer.phone_number, EXCLUDED.phone_number),
                            intent_status = 'CONVERTED',
                            is_converted = true,
                            updated_at = NOW()
                        """)
                .param(tid)
                .param(conv)
                .param(phone)
                .param(name.isEmpty() ? null : name)
                .update();
    }

    @Override
    @Transactional
    public void applyLeadIntentFromClassification(
            TenantId tenantId, String conversationId, ChatMainIntent intent, Instant classifiedAt) {
        if (conversationId == null || conversationId.isBlank() || intent == null) {
            return;
        }
        String label = intent.dbValue();
        if (label == null || label.isBlank()) {
            return;
        }
        ensureOnConversationStart(tenantId, conversationId, Optional.empty());
        Timestamp at = Timestamp.from(classifiedAt != null ? classifiedAt : Instant.now());
        jdbcClient
                .sql(
                        """
                        UPDATE crm_customer SET
                            last_intent = ?,
                            last_detected_intent = ?,
                            last_intent_at = ?,
                            is_converted = CASE WHEN intent_status = 'CONVERTED' THEN false ELSE is_converted END,
                            intent_status = CASE
                                WHEN intent_status IN ('ASSIGNED', 'DISMISSED') THEN intent_status
                                ELSE 'OPEN'
                            END,
                            updated_at = NOW()
                        WHERE tenant_id = ? AND conversation_id = ?
                        """)
                .param(label.strip())
                .param(label.strip())
                .param(at)
                .param(tenantId.value())
                .param(conversationId.strip())
                .update();
    }

    @Override
    @Transactional
    public void updateLeadScore(TenantId tenantId, String conversationId, int leadScore) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        int s = Math.min(100, Math.max(0, leadScore));
        jdbcClient
                .sql(
                        """
                        UPDATE crm_customer SET lead_score = ?, updated_at = NOW()
                        WHERE tenant_id = ? AND conversation_id = ?
                        """)
                .param(s)
                .param(tenantId.value())
                .param(conversationId.strip())
                .update();
    }

    @Override
    @Transactional
    public boolean assumePendingLead(TenantId tenantId, UUID customerId) {
        int n =
                jdbcClient
                        .sql(
                                """
                                UPDATE crm_customer SET intent_status = 'ASSIGNED', updated_at = NOW()
                                WHERE tenant_id = ? AND id = ? AND intent_status IN ('PENDING_LEAD', 'HOT_LEAD')
                                """)
                        .param(tenantId.value())
                        .param(customerId)
                        .update();
        return n > 0;
    }

    @Override
    @Transactional
    public int promoteStaleOpenLeadsToPending(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            return 0;
        }
        long seconds = window.getSeconds();
        return jdbcClient
                .sql(
                        """
                        UPDATE crm_customer SET intent_status = 'PENDING_LEAD', updated_at = NOW()
                        WHERE intent_status = 'OPEN'
                          AND last_intent_at IS NOT NULL
                          AND last_intent_at < NOW() - (?::bigint * INTERVAL '1 second')
                        """)
                .param(seconds)
                .update();
    }

    @Override
    @Transactional
    public int markStaleBudgetSchedulingAsHotLeadWithoutAppointment() {
        return jdbcClient
                .sql(
                        """
                        UPDATE crm_customer c
                        SET intent_status = 'HOT_LEAD', updated_at = NOW()
                        WHERE NOT c.is_converted
                          AND COALESCE(NULLIF(TRIM(c.last_detected_intent), ''), c.last_intent) IN ('Orçamento', 'Agendamento')
                          AND c.last_intent_at IS NOT NULL
                          AND c.last_intent_at < NOW() - INTERVAL '1 hour'
                          AND c.intent_status NOT IN ('ASSIGNED', 'DISMISSED', 'CONVERTED', 'HOT_LEAD')
                          AND NOT EXISTS (
                              SELECT 1 FROM tenant_appointments ta
                              WHERE ta.tenant_id = c.tenant_id
                                AND ta.conversation_id = c.conversation_id
                                AND ta.created_at >= c.last_intent_at
                          )
                        """)
                .update();
    }

    @Override
    @Transactional
    public void updateInternalNotes(TenantId tenantId, UUID customerId, String internalNotes) {
        String notes = internalNotes == null ? "" : internalNotes;
        if (notes.length() > INTERNAL_NOTES_MAX_CHARS) {
            notes = notes.substring(0, INTERNAL_NOTES_MAX_CHARS);
        }
        jdbcClient
                .sql(
                        """
                        UPDATE crm_customer SET internal_notes = ?, updated_at = NOW()
                        WHERE id = ? AND tenant_id = ?
                        """)
                .param(notes.isEmpty() ? null : notes)
                .param(customerId)
                .param(tenantId.value())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CrmCustomerRecord> findByTenantAndConversationId(TenantId tenantId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, conversation_id, phone_number, full_name, email,
                               first_interaction, total_appointments, internal_notes,
                               last_intent, last_detected_intent, lead_score, is_converted,
                               intent_status, last_intent_at
                        FROM crm_customer
                        WHERE tenant_id = ? AND conversation_id = ?
                        """)
                .param(tenantId.value())
                .param(conversationId.strip())
                .query(this::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrmCustomerRecord> listPendingLeadOpportunities(TenantId tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, conversation_id, phone_number, full_name, email,
                               first_interaction, total_appointments, internal_notes,
                               last_intent, last_detected_intent, lead_score, is_converted,
                               intent_status, last_intent_at
                        FROM crm_customer
                        WHERE tenant_id = ?
                          AND NOT is_converted
                          AND intent_status IN ('PENDING_LEAD', 'HOT_LEAD')
                        ORDER BY lead_score DESC, last_intent_at DESC NULLS LAST
                        """)
                .param(tenantId.value())
                .query(this::mapRow)
                .list();
    }

    private CrmCustomerRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lit = rs.getTimestamp("last_intent_at");
        return new CrmCustomerRecord(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("conversation_id"),
                rs.getString("phone_number"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getTimestamp("first_interaction").toInstant(),
                rs.getInt("total_appointments"),
                rs.getString("internal_notes"),
                rs.getString("last_intent"),
                rs.getString("last_detected_intent"),
                rs.getInt("lead_score"),
                rs.getBoolean("is_converted"),
                rs.getString("intent_status"),
                lit != null ? lit.toInstant() : null);
    }
}
