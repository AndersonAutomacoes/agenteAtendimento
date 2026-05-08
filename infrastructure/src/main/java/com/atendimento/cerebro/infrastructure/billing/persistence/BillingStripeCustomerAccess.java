package com.atendimento.cerebro.infrastructure.billing.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve o {@code stripe_customer_id} quando o utilizador é dono da cobrança do tenant (registo em
 * {@code stripe_customer} ou {@code portal_user.billing_owner}).
 */
@Component
public class BillingStripeCustomerAccess {

    private final JdbcClient jdbcClient;

    public BillingStripeCustomerAccess(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Utilizador deve ser o {@code owner_firebase_uid} na tabela {@code stripe_customer} <em>ou</em> ter
     * {@code billing_owner} em {@code portal_user} para o par (tenant, firebase uid).
     */
    @Transactional(readOnly = true)
    public Optional<String> findStripeCustomerIdForBillingActor(String tenantId, String firebaseUid) {
        if (tenantId == null
                || tenantId.isBlank()
                || firebaseUid == null
                || firebaseUid.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT sc.stripe_customer_id
                        FROM stripe_customer sc
                        WHERE sc.tenant_id = ?
                          AND (
                               sc.owner_firebase_uid = ?
                            OR EXISTS (
                                SELECT 1
                                FROM portal_user pu
                                WHERE pu.tenant_id = sc.tenant_id
                                  AND pu.firebase_uid = ?
                                  AND pu.billing_owner = TRUE
                               )
                          )
                        """)
                .param(tenantId.strip())
                .param(firebaseUid.strip())
                .param(firebaseUid.strip())
                .query(String.class)
                .optional();
    }
}
