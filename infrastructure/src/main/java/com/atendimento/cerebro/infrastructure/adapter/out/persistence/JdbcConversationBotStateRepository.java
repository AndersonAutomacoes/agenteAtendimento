package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcConversationBotStateRepository implements ConversationBotStatePort {

    private final JdbcClient jdbcClient;

    public JdbcConversationBotStateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Chave alinhada ao sufixo {@code wa-&lt;dígitos&gt;} do {@link com.atendimento.cerebro.domain.conversation.ConversationId}
     * e ao webhook WhatsApp: apenas dígitos quando existirem; caso contrário o texto normalizado (ex.: testes).
     */
    static String canonicalPhoneKey(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        String digits = onlyDigits(phoneNumber);
        if (!digits.isEmpty()) {
            return digits;
        }
        return phoneNumber.strip();
    }

    private static String onlyDigits(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isBotEnabled(TenantId tenantId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return true;
        }
        String key = canonicalPhoneKey(phoneNumber);
        if (key.isEmpty()) {
            return true;
        }
        return jdbcClient
                .sql(
                        """
                        SELECT is_bot_enabled FROM conversation
                        WHERE tenant_id = ? AND phone_number = ?
                        """)
                .param(tenantId.value())
                .param(key)
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    @Override
    @Transactional
    public void setBotEnabled(TenantId tenantId, String phoneNumber, boolean enabled) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String key = canonicalPhoneKey(phoneNumber);
        if (key.isEmpty()) {
            return;
        }
        jdbcClient
                .sql(
                        """
                        INSERT INTO conversation (tenant_id, phone_number, is_bot_enabled, updated_at)
                        VALUES (?, ?, ?, now())
                        ON CONFLICT (tenant_id, phone_number) DO UPDATE
                        SET is_bot_enabled = EXCLUDED.is_bot_enabled, updated_at = now()
                        """)
                .param(tenantId.value())
                .param(key)
                .param(enabled)
                .update();
    }

    @Override
    @Transactional
    public void enableBotWithResumeContextHint(TenantId tenantId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String key = canonicalPhoneKey(phoneNumber);
        if (key.isEmpty()) {
            return;
        }
        jdbcClient
                .sql(
                        """
                        INSERT INTO conversation (tenant_id, phone_number, is_bot_enabled, resume_ai_after_human, updated_at)
                        VALUES (?, ?, true, true, now())
                        ON CONFLICT (tenant_id, phone_number) DO UPDATE
                        SET is_bot_enabled = true, resume_ai_after_human = true, updated_at = now()
                        """)
                .param(tenantId.value())
                .param(key)
                .update();
    }

    @Override
    @Transactional
    public boolean consumeResumeAiContextIfPending(TenantId tenantId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        String key = canonicalPhoneKey(phoneNumber);
        if (key.isEmpty()) {
            return false;
        }
        int n =
                jdbcClient
                        .sql(
                                """
                                UPDATE conversation
                                SET resume_ai_after_human = false, updated_at = now()
                                WHERE tenant_id = ? AND phone_number = ? AND resume_ai_after_human = true
                                """)
                        .param(tenantId.value())
                        .param(key)
                        .update();
        return n > 0;
    }

    @Override
    public Map<String, Boolean> resolveBotEnabledForPhones(TenantId tenantId, Collection<String> phoneNumbers) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String p : phoneNumbers) {
            if (p != null && !p.isBlank()) {
                distinct.add(p.strip());
            }
        }
        Map<String, Boolean> out = new HashMap<>();
        for (String p : distinct) {
            out.put(p, true);
        }
        if (distinct.isEmpty()) {
            return out;
        }
        Set<String> canonicalForQuery = new LinkedHashSet<>();
        for (String p : distinct) {
            String c = canonicalPhoneKey(p);
            if (!c.isEmpty()) {
                canonicalForQuery.add(c);
            }
        }
        if (canonicalForQuery.isEmpty()) {
            return out;
        }
        String inClause = canonicalForQuery.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql =
                "SELECT phone_number, is_bot_enabled FROM conversation WHERE tenant_id = ? AND phone_number IN ("
                        + inClause
                        + ")";
        var spec = jdbcClient.sql(sql).param(tenantId.value());
        for (String c : canonicalForQuery) {
            spec = spec.param(c);
        }
        Map<String, Boolean> byCanonical = new HashMap<>();
        spec.query((rs, rowNum) -> Map.entry(rs.getString("phone_number"), rs.getBoolean("is_bot_enabled")))
                .list()
                .forEach(e -> byCanonical.put(e.getKey(), e.getValue()));
        for (String p : distinct) {
            String c = canonicalPhoneKey(p);
            if (!c.isEmpty() && byCanonical.containsKey(c)) {
                out.put(p, byCanonical.get(c));
            }
        }
        return out;
    }
}
