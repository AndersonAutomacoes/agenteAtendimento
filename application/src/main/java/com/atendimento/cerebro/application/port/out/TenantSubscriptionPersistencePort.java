package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import java.util.Optional;

/**
 * Espelho Stripe de assinatura por tenant + sincronização do {@code profile_level} no portal.
 *
 * <p>Política Task 7: ao aplicar um snapshot vindo do Stripe, actualiza {@code tenant_configuration.profile_level}
 * e todas as linhas {@code portal_user.profile_level} desse tenant para {@code BASIC|PRO|ULTRA} conforme o tier
 * cobrado — substitui valores anteriores (incl. COMERCIAL) sempre que o webhook/checkout enviar estado Stripe.
 */
public interface TenantSubscriptionPersistencePort {

    void upsertAndApplyProfileLevel(TenantSubscriptionSnapshot snapshot);

    Optional<TenantSubscriptionSnapshot> findByTenantId(String tenantId);
}
