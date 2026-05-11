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
     *
     * <p>Se ainda não existir linha em {@code stripe_customer} mas já houver espelho em {@code tenant_subscription}
     * (ex.: dados antigos antes do upsert conjunto), devolve {@code tenant_subscription.stripe_customer_id} quando o
     * utilizador for {@code billing_owner}.
     */
    @Transactional(readOnly = true)
    public Optional<String> findStripeCustomerIdForBillingActor(String tenantId, String firebaseUid) {
        if (tenantId == null
                || tenantId.isBlank()
                || firebaseUid == null
                || firebaseUid.isBlank()) {
            return Optional.empty();
        }
        String t = tenantId.strip();
        String u = firebaseUid.strip();
        Optional<String> fromStripeCustomer =
                jdbcClient
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
                        .param(t)
                        .param(u)
                        .param(u)
                        .query(String.class)
                        .optional();
        if (fromStripeCustomer.isPresent()) {
            return fromStripeCustomer;
        }
        return jdbcClient
                .sql(
                        """
                        SELECT ts.stripe_customer_id
                        FROM tenant_subscription ts
                        INNER JOIN portal_user pu
                            ON pu.tenant_id = ts.tenant_id
                           AND pu.firebase_uid = ?
                        WHERE ts.tenant_id = ?
                          AND NOT EXISTS (SELECT 1 FROM stripe_customer sc WHERE sc.tenant_id = ts.tenant_id)
                          AND (
                              pu.billing_owner
                              OR (
                                  NOT EXISTS (
                                      SELECT 1
                                      FROM portal_user b
                                      WHERE b.tenant_id = ts.tenant_id
                                        AND b.billing_owner
                                  )
                                  AND pu.id = (
                                      SELECT pu2.id
                                      FROM portal_user pu2
                                      WHERE pu2.tenant_id = ts.tenant_id
                                      ORDER BY pu2.created_at ASC
                                      LIMIT 1
                                  )
                              )
                          )
                        """)
                .param(u)
                .param(t)
                .query(String.class)
                .optional();
    }

    /**
     * Quem pode criar o primeiro cliente Stripe (checkout sem linha prévia): {@code billing_owner}, ou o primeiro
     * utilizador do tenant quando ainda não existe dono explícito.
     */
    @Transactional(readOnly = true)
    public boolean canProvisionStripeCustomerForCheckout(String tenantId, String firebaseUid) {
        if (tenantId == null
                || tenantId.isBlank()
                || firebaseUid == null
                || firebaseUid.isBlank()) {
            return false;
        }
        String t = tenantId.strip();
        String u = firebaseUid.strip();
        return Boolean.TRUE.equals(
                jdbcClient
                        .sql(
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM portal_user me
                                    WHERE me.tenant_id = ?
                                      AND me.firebase_uid = ?
                                      AND (
                                          me.billing_owner
                                          OR (
                                              NOT EXISTS (
                                                  SELECT 1
                                                  FROM portal_user b
                                                  WHERE b.tenant_id = me.tenant_id
                                                    AND b.billing_owner
                                              )
                                              AND me.id = (
                                                  SELECT pu.id
                                                  FROM portal_user pu
                                                  WHERE pu.tenant_id = me.tenant_id
                                                  ORDER BY pu.created_at ASC
                                                  LIMIT 1
                                              )
                                          )
                                      )
                                )
                                """)
                        .param(t)
                        .param(u)
                        .query(Boolean.class)
                        .single());
    }

    /**
     * Liga tenant a um {@code stripe_customer_id} após espelho Stripe (webhook), preenchendo {@code owner_firebase_uid}
     * com o {@code billing_owner} ou o primeiro utilizador do tenant.
     */
    @Transactional
    public void upsertStripeCustomerFromSubscriptionSync(String tenantId, String stripeCustomerId) {
        if (tenantId == null
                || tenantId.isBlank()
                || stripeCustomerId == null
                || stripeCustomerId.isBlank()) {
            return;
        }
        String t = tenantId.strip();
        String c = stripeCustomerId.strip();
        jdbcClient
                .sql(
                        """
                        INSERT INTO stripe_customer (tenant_id, stripe_customer_id, owner_firebase_uid)
                        SELECT ?, ?, owner_uid
                        FROM (
                            SELECT COALESCE(
                                (SELECT firebase_uid
                                 FROM portal_user
                                 WHERE tenant_id = ? AND billing_owner = TRUE
                                 LIMIT 1),
                                (SELECT firebase_uid
                                 FROM portal_user
                                 WHERE tenant_id = ?
                                 ORDER BY created_at ASC
                                 LIMIT 1)
                            ) AS owner_uid
                        ) x
                        WHERE x.owner_uid IS NOT NULL
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            stripe_customer_id = EXCLUDED.stripe_customer_id,
                            owner_firebase_uid = COALESCE(
                                NULLIF(TRIM(stripe_customer.owner_firebase_uid), ''),
                                EXCLUDED.owner_firebase_uid),
                            updated_at = NOW()
                        """)
                .param(t)
                .param(c)
                .param(t)
                .param(t)
                .update();
    }

    /** Primeiro checkout: grava o cliente Stripe criado na API, associado ao dono da cobrança. */
    @Transactional
    public void upsertStripeCustomerExplicit(String tenantId, String stripeCustomerId, String ownerFirebaseUid) {
        if (tenantId == null
                || tenantId.isBlank()
                || stripeCustomerId == null
                || stripeCustomerId.isBlank()
                || ownerFirebaseUid == null
                || ownerFirebaseUid.isBlank()) {
            return;
        }
        jdbcClient
                .sql(
                        """
                        INSERT INTO stripe_customer (tenant_id, stripe_customer_id, owner_firebase_uid)
                        VALUES (?, ?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            stripe_customer_id = EXCLUDED.stripe_customer_id,
                            owner_firebase_uid = COALESCE(
                                NULLIF(TRIM(stripe_customer.owner_firebase_uid), ''),
                                EXCLUDED.owner_firebase_uid),
                            updated_at = NOW()
                        """)
                .param(tenantId.strip())
                .param(stripeCustomerId.strip())
                .param(ownerFirebaseUid.strip())
                .update();
    }
}
