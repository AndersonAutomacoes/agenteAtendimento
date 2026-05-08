# Stripe tenant billing (Checkout + webhooks + entitlement) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cobrar assinaturas Basic/Pro/Ultra (mensal/anual) via Stripe por tenant, onde o pagador é o dono do tenant, sincronizar estado com webhooks idempotentes e bloquear uso do portal fora das rotas de exceção definidas na spec aprovada.

**Architecture:** PostgreSQL (Flyway) guarda `stripe_customer`, `tenant_subscription` e `stripe_webhook_event`. A regra “posso usar o produto?” fica em um serviço puro no módulo `application` (desacoplado do SDK Stripe). O módulo `infrastructure` encapsula Stripe Java SDK, controller de webhook com payload bruto + assinatura HMAC, endpoints REST de Checkout/Portal e atualização de `portal_user` / `tenant_configuration.profile_level` quando a assinatura for válida. O Next.js chama só APIs backend e aplica guard de rotas alinhado ao §12.3 da spec.

**Tech stack:** Java 21, Spring Boot 3.4.x, Maven multimódulo (`domain`, `application`, `infrastructure`, `bootstrap`), PostgreSQL + Flyway, `com.stripe:stripe-java`, Firebase auth no portal, Next.js (App Router) em `atendimento-frontEnd/atendimento-frontend`.

**Spec aprovada:** [`docs/superpowers/specs/2026-05-08-stripe-tenant-billing-design.md`](../../specs/2026-05-08-stripe-tenant-billing-design.md)

---

## File structure (creates / modifies)

| Responsibility | Path |
|----------------|------|
| Billing schema + dono do billing | **Create** `bootstrap/src/main/resources/db/migration/V39__stripe_billing.sql` |
| Tier alinhado ao produto (Stripe) | **Create** `domain/src/main/java/com/atendimento/cerebro/domain/billing/BillingPlanTier.java` |
| Snapshot neutro p/ aplicação | **Create** `application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantSubscriptionSnapshot.java` |
| Decisão entitlement | **Create** `application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantEntitlementDecision.java` |
| Motor de entitlement (puro) | **Create** `application/src/main/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluator.java` |
| Teste entitlement | **Create** `application/src/test/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluatorTest.java` |
| Porta saída billing | **Create** `application/src/main/java/com/atendimento/cerebro/application/port/out/TenantSubscriptionPersistencePort.java` |
| Porta saída webhook idempotência | **Create** `application/src/main/java/com/atendimento/cerebro/application/port/out/StripeWebhookEventPersistencePort.java` |
| Caso de uso sync | **Create** `application/src/main/java/com/atendimento/cerebro/application/service/billing/BillingSubscriptionSyncService.java` |
| Stripe dep | **Modify** `infrastructure/pom.xml` (adicionar dependência `stripe-java`) |
| JPA entities + repos | **Create** `infrastructure/.../billing/persistence/*.java` (entities, Spring Data JPA) |
| Config price map | **Modify** `bootstrap/src/main/resources/application.yml` + **Create** `@ConfigurationProperties` em infrastructure |
| Webhook HTTP | **Create** `infrastructure/.../billing/StripeWebhookController.java` |
| Checkout/Portal HTTP | **Create** `infrastructure/.../billing/BillingSessionController.java` |
| Segurança (permit webhook, proteger checkout) | **Modify** `infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java` |
| Filtro entitlement (depois do Firebase, antes do uso) | **Create** `infrastructure/.../security/BillingEntitlementWebFilter.java` (ou extensão de chain existente documentada no passo) |
| Página planos | **Create** `atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(marketing)/pricing/page.tsx` (ajuste grupo de rotas se já existir `(marketing)`) |
| Pós-checkout / suspenso | **Create** `.../billing/success/page.tsx`, `.../billing/suspended/page.tsx` |
| Guard Next | **Create** `atendimento-frontEnd/atendimento-frontend/src/middleware.ts` |

Arquivos exactos de domínio devem seguir o pacote `com.atendimento.cerebro` já usado no projeto.

---

### Task 1: Flyway `V39` — tabelas Stripe + coluna `billing_owner` em `portal_user`

**Files:**

- Create: `bootstrap/src/main/resources/db/migration/V39__stripe_billing.sql`
- Modify: (nenhum além do novo ficheiro Flyway)

**Conteúdo SQL (completo para o ficheiro):**

```sql
-- Um dono de cobrança por tenant (Stripe customer / checkout).
ALTER TABLE portal_user
    ADD COLUMN IF NOT EXISTS billing_owner BOOLEAN NOT NULL DEFAULT FALSE;

-- Apenas um utilizador por tenant como dono da cobrança.
CREATE UNIQUE INDEX IF NOT EXISTS ux_portal_user_billing_owner_one_per_tenant
    ON portal_user (tenant_id)
    WHERE billing_owner;

-- Mais antigo por tenant torna-se dono quando ainda não existe dono explícito.
WITH first_owner AS (
    SELECT DISTINCT ON (tenant_id) id
    FROM portal_user
    ORDER BY tenant_id, created_at ASC
)
UPDATE portal_user pu
SET billing_owner = TRUE
FROM first_owner fo
WHERE pu.id = fo.id
  AND NOT EXISTS (
        SELECT 1 FROM portal_user p2 WHERE p2.tenant_id = pu.tenant_id AND p2.billing_owner
    );

CREATE TABLE stripe_customer (
    tenant_id               VARCHAR(512) PRIMARY KEY REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    stripe_customer_id      VARCHAR(255) NOT NULL UNIQUE,
    owner_firebase_uid      VARCHAR(128) NOT NULL REFERENCES portal_user (firebase_uid),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_subscription (
    tenant_id                 VARCHAR(512) PRIMARY KEY REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    stripe_subscription_id    VARCHAR(255) NOT NULL UNIQUE,
    stripe_customer_id        VARCHAR(255) NOT NULL,
    stripe_status             VARCHAR(32)  NOT NULL,
    tier                      VARCHAR(16)  NOT NULL CHECK (tier IN ('BASIC', 'PRO', 'ULTRA')),
    price_id                  VARCHAR(255) NOT NULL,
    billing_interval          VARCHAR(16)  NOT NULL CHECK (billing_interval IN ('MONTH', 'YEAR')),
    current_period_start      TIMESTAMPTZ NOT NULL,
    current_period_end        TIMESTAMPTZ NOT NULL,
    cancel_at_period_end      BOOLEAN NOT NULL DEFAULT FALSE,
    past_due_since            TIMESTAMPTZ NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_subscription_customer ON tenant_subscription (stripe_customer_id);

CREATE TABLE stripe_webhook_event (
    event_id         VARCHAR(255) PRIMARY KEY,
    event_type       VARCHAR(128) NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at     TIMESTAMPTZ NULL,
    processing_error TEXT NULL
);
```

- [ ] **Step 1:** Colar o SQL acima em `V39__stripe_billing.sql`. Garantir no serviço de sync que `portal_user.tenant_id` correspondente ao `owner_firebase_uid` coincide com `stripe_customer.tenant_id` antes de gravar (`firebase_uid` já é `UNIQUE` em `portal_user`, ver `V13__portal_user_and_tenant_invite.sql`).

- [ ] **Step 2:** Subir Postgres local e aplicar migrações

Run:

```bash
mvn -q -pl bootstrap spring-boot:run -Dspring-boot.run.arguments="--spring.flyway.validate-on-migrate=true" 2>&1 | findstr /I "flyway error"
```

Expected: aplicação arranca sem erro Flyway **ou** executar apenas validação com profile de teste que rode Flyway (ajustar comando ao vosso `docker compose` se o arranque completo for pesado). Alternativa mínima:

```bash
mvn -q -pl bootstrap -am test -Dtest=none 2>&1
```

se existir teste de contexto; caso contrário use o comando de `docker`/`flyway` que o projeto já documenta.

- [ ] **Step 3: Commit**

```bash
git add bootstrap/src/main/resources/db/migration/V39__stripe_billing.sql
git commit -m "feat(db): stripe billing tables and portal billing_owner"
```

---

### Task 2: Domínio `BillingPlanTier` + DTOs de snapshot e decisão

**Files:**

- Create: `domain/src/main/java/com/atendimento/cerebro/domain/billing/BillingPlanTier.java`
- Create: `application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantSubscriptionSnapshot.java`
- Create: `application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantEntitlementDecision.java`

**Código:**

`BillingPlanTier.java`:

```java
package com.atendimento.cerebro.domain.billing;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;

public enum BillingPlanTier {
    BASIC,
    PRO,
    ULTRA;

    public ProfileLevel toProfileLevel() {
        return switch (this) {
            case BASIC -> ProfileLevel.BASIC;
            case PRO -> ProfileLevel.PRO;
            case ULTRA -> ProfileLevel.ULTRA;
        };
    }

    public static BillingPlanTier fromProfileLevel(ProfileLevel level) {
        if (level == null) throw new IllegalArgumentException("level");
        return switch (level) {
            case BASIC -> BASIC;
            case PRO -> PRO;
            case ULTRA -> ULTRA;
            case COMERCIAL -> throw new IllegalArgumentException("COMERCIAL is not a Stripe-paid tier");
        };
    }
}
```

`TenantSubscriptionSnapshot.java`:

```java
package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;

public record TenantSubscriptionSnapshot(
        String tenantId,
        String stripeSubscriptionId,
        String stripeStatus,
        BillingPlanTier tier,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant pastDueSince) {}
```

`TenantEntitlementDecision.java`:

```java
package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;

public record TenantEntitlementDecision(boolean allowed,
                                        BillingPlanTier tier,
                                        String reasonCode) {}
```

- [ ] **Step 1:** Criar os três ficheiros com o conteúdo acima.

- [ ] **Step 2:** Compilar módulos

Run:

```bash
mvn -q -pl domain,application -am compile
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/java/com/atendimento/cerebro/domain/billing/BillingPlanTier.java ^
  application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantSubscriptionSnapshot.java ^
  application/src/main/java/com/atendimento/cerebro/application/dto/billing/TenantEntitlementDecision.java

git commit -m "feat(billing): BillingPlanTier and entitlement DTOs"
```

(No PowerShell usar `git add path1 path2 path3` em linha única sem `^`.)

---

### Task 3: `TenantEntitlementEvaluator` + testes (TDD — spec §6 + grace 7d)

**Files:**

- Create: `application/src/main/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluator.java`
- Create: `application/src/test/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluatorTest.java`

**Test (escrever primeiro):**

```java
package com.atendimento.cerebro.application.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.billing.TenantEntitlementDecision;
import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TenantEntitlementEvaluatorTest {

    private final TenantEntitlementEvaluator evaluator = new TenantEntitlementEvaluator(7);

    @Test
    void active_allows_through_end_of_period() {
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-01T00:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "active",
                BillingPlanTier.PRO,
                start,
                end,
                false,
                null);
        TenantEntitlementDecision d = evaluator.evaluate(snap, Instant.parse("2026-05-15T00:00:00Z"));
        assertThat(d.allowed()).isTrue();
        assertThat(d.tier()).isEqualTo(BillingPlanTier.PRO);
        assertThat(d.reasonCode()).isEqualTo("OK_ACTIVE");
    }

    @Test
    void canceled_at_period_end_still_allowed_while_active() {
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "active",
                BillingPlanTier.BASIC,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                true,
                null);
        TenantEntitlementDecision d = evaluator.evaluate(snap, Instant.parse("2026-05-20T00:00:00Z"));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void past_due_within_grace_allows() {
        Instant pastDueSince = Instant.parse("2026-05-08T12:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "past_due",
                BillingPlanTier.ULTRA,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                pastDueSince);
        TenantEntitlementDecision d = evaluator.evaluate(snap, pastDueSince.plus(3, ChronoUnit.DAYS));
        assertThat(d.allowed()).isTrue();
        assertThat(d.reasonCode()).isEqualTo("OK_PAST_DUE_GRACE");
    }

    @Test
    void past_due_after_grace_denies() {
        Instant pastDueSince = Instant.parse("2026-05-08T12:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "past_due",
                BillingPlanTier.ULTRA,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                pastDueSince);
        TenantEntitlementDecision d = evaluator.evaluate(snap, pastDueSince.plus(8, ChronoUnit.DAYS));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("BLOCKED_PAST_DUE_GRACE_EXPIRED");
    }

    @Test
    void no_subscription_denies() {
        TenantEntitlementDecision d = evaluator.evaluate(null, Instant.now());
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("BLOCKED_NO_SUBSCRIPTION");
    }
}
```

**Implementação mínima:**

```java
package com.atendimento.cerebro.application.service.billing;

import com.atendimento.cerebro.application.dto.billing.TenantEntitlementDecision;
import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TenantEntitlementEvaluator {

    private final int pastDueGraceDays;

    public TenantEntitlementEvaluator(int pastDueGraceDays) {
        this.pastDueGraceDays = pastDueGraceDays;
    }

    public TenantEntitlementDecision evaluate(TenantSubscriptionSnapshot subscription, Instant now) {
        if (subscription == null) {
            return new TenantEntitlementDecision(false, null, "BLOCKED_NO_SUBSCRIPTION");
        }
        String status = subscription.stripeStatus();
        if ("active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status)) {
            if (!now.isBefore(subscription.currentPeriodStart()) && now.isBefore(subscription.currentPeriodEnd())) {
                return new TenantEntitlementDecision(true, subscription.tier(), "OK_ACTIVE");
            }
            if (!now.isBefore(subscription.currentPeriodEnd())) {
                return blocked("BLOCKED_PERIOD_ENDED");
            }
            return blocked("BLOCKED_BEFORE_PERIOD_START");
        }
        if ("past_due".equalsIgnoreCase(status)) {
            Instant since = subscription.pastDueSince() != null ? subscription.pastDueSince() : now;
            long days = ChronoUnit.DAYS.between(since, now);
            if (days < pastDueGraceDays) {
                return new TenantEntitlementDecision(true, subscription.tier(), "OK_PAST_DUE_GRACE");
            }
            return new TenantEntitlementDecision(false, null, "BLOCKED_PAST_DUE_GRACE_EXPIRED");
        }
        if ("canceled".equalsIgnoreCase(status) || "unpaid".equalsIgnoreCase(status)) {
            return blocked("BLOCKED_SUBSCRIPTION_TERMINAL_STATE");
        }
        return blocked("BLOCKED_UNSUPPORTED_STATUS_" + status);
    }

    private static TenantEntitlementDecision blocked(String code) {
        return new TenantEntitlementDecision(false, null, code);
    }
}
```

- [ ] **Step 1:** Criar `TenantEntitlementEvaluatorTest.java`.

- [ ] **Step 2:** Correr só este teste (deve falhar: classe não existe)

Run:

```bash
mvn -q -pl application test -Dtest=TenantEntitlementEvaluatorTest
```

Expected: `ClassNotFound` / falha compilação `TenantEntitlementEvaluator`.

- [ ] **Step 3:** Adicionar `TenantEntitlementEvaluator.java`.

- [ ] **Step 4:** Voltar a correr testes — deve passar os 5 casos.

Run:

```bash
mvn -q -pl application test -Dtest=TenantEntitlementEvaluatorTest
```

Expected: `Tests run: 5, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluator.java application/src/test/java/com/atendimento/cerebro/application/service/billing/TenantEntitlementEvaluatorTest.java

git commit -m "feat(billing): entitlement evaluator with grace period"
```

---

### Task 4: Maven — dependência Stripe só em `infrastructure`

**Files:**

- Modify: `infrastructure/pom.xml` (dentro de `<dependencies>`)

Adicionar bloco:

```xml
        <dependency>
            <groupId>com.stripe</groupId>
            <artifactId>stripe-java</artifactId>
            <version>28.3.1</version>
        </dependency>
```

- [ ] **Step 1:** Inserir o bloco XML acima em `infrastructure/pom.xml`.

- [ ] **Step 2:** Resolver dependências

Run:

```bash
mvn -q -pl infrastructure dependency:resolve
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add infrastructure/pom.xml

git commit -m "build: add stripe-java to infrastructure module"
```

---

### Task 5: Webhook `POST /v1/billing/webhook/stripe` — corpo bruto + idempotência (esqueleto compilável)

**Files:**

- Create: `infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/webhook/StripeWebhookController.java`
- Create: `infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/webhook/StripeValidatedWebhookSink.java`
- Modify: `infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java`

**`StripeValidatedWebhookSink.java`** — em Task 8 substituis o interior por chamadas ao `BillingSubscriptionSyncService`; por agora apenas regista para verificar o fluxo.

```java
package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StripeValidatedWebhookSink {

    private static final Logger log = LoggerFactory.getLogger(StripeValidatedWebhookSink.class);

    public void accept(Event event) {
        log.info("stripe event accepted id={} type={}", event.getId(), event.getType());
    }
}
```

**Controller:**

```java
package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final String webhookSecret;
    private final StripeValidatedWebhookSink sink;

    public StripeWebhookController(
            @Value("${stripe.webhook-secret:}") String webhookSecret, StripeValidatedWebhookSink sink) {
        this.webhookSecret = webhookSecret;
        this.sink = sink;
    }

    @PostMapping(path = "/v1/billing/webhook/stripe", consumes = "application/json; charset=utf-8")
    public ResponseEntity<String> receive(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        if (stripeSignature == null || stripeSignature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing signature");
        }
        try {
            Event event = Webhook.constructEvent(payload, stripeSignature, webhookSecret);
            sink.accept(event);
            return ResponseEntity.ok("received");
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }
    }
}
```

**Nota obrigatória:** O método usa `byte[]` para preservar payload bruto. Garantir que nenhum filtro global parseia o corpo antes; se necessário, excluir este path em `FirebasePortalAuthenticationFilter` / consumo de InputStream conforme código existente.

**Security (`SecurityConfiguration` — acrescentar encadeamentos após outros `requestMatchers`):**

```java
                                .requestMatchers(HttpMethod.POST, "/v1/billing/webhook/stripe")
                                .permitAll()
```

(Importar `HttpMethod` se ainda não estiver.)

- [ ] **Step 1:** Criar o controller acima.

- [ ] **Step 2:** Ajustar segurança com `permitAll` ao path indicado.

- [ ] **Step 3:** Se ainda não existir chave `stripe.webhook-secret` em `bootstrap/src/main/resources/application.yml`, fundir o bloco da **Task 6** (evitar duplicar chaves `stripe` no mesmo ficheiro).

- [ ] **Step 4:** Compilar

Run:

```bash
mvn -q -pl infrastructure,bootstrap -am compile -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/webhook/StripeWebhookController.java infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/webhook/StripeValidatedWebhookSink.java infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java

git commit -m "feat(billing): stripe webhook endpoint stub with signature verification"
```

---

### Task 6: Config propriedades `stripe.*` para API key e mapa opcional Price → tier

**Files:**

- Modify: `bootstrap/src/main/resources/application.yml`
- Create: `infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/BillingStripeProperties.java`

```java
package com.atendimento.cerebro.infrastructure.billing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record BillingStripeProperties(
        String secretKey,
        String webhookSecret,
        String successUrl,
        String cancelUrl,
        LinkedHashMap<String, String> priceTier) {

    /** Mapa Stripe price_id → BASIC | PRO | ULTRA */
    public Map<String, String> priceTierNonNull() {
        return priceTier != null ? Map.copyOf(priceTier) : Map.of();
    }
}
```

**YAML (snippet a fundir sob `stripe:`):**

```yaml
stripe:
  secret-key: ${STRIPE_SECRET_KEY:}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
  success-url: ${STRIPE_CHECKOUT_SUCCESS_URL:http://localhost:3000/pt-BR/billing/success}
  cancel-url: ${STRIPE_CHECKOUT_CANCEL_URL:http://localhost:3000/pt-BR/pricing}
  price-tier:
    price_REPLACE_BASIC_MONTHLY: BASIC
    price_REPLACE_BASIC_YEARLY: BASIC
    price_REPLACE_PRO_MONTHLY: PRO
    price_REPLACE_ULTRA_YEARLY: ULTRA
```

Instruções: substituir `price_REPLACE_*` pelos IDs reais do Stripe Dashboard após criar Prices.

**`BillingStripeConfiguration.java` (completo):**

```java
package com.atendimento.cerebro.infrastructure.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingStripeProperties.class)
public class BillingStripeConfiguration {}
```

Registar este `@Configuration` no scan do pacote `com.atendimento.cerebro.infrastructure` (já coberto pelo `@SpringBootApplication` do bootstrap se o pacote base for ascendente).

- [ ] **Steps 1–3:** criar classe, registar bean, atualizar YAML.

- [ ] **Step 4: Commit**

```bash
git add bootstrap/src/main/resources/application.yml infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/BillingStripeProperties.java infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/billing/BillingStripeConfiguration.java

git commit -m "feat(billing): Stripe configuration properties"
```

---

### Task 7: Persistência JPA (`TenantSubscriptionPersistencePort`) + adaptador que alimenta `tenant_configuration.profile_level`

**Files:**

- Create: interfaces em `application/.../port/out/TenantSubscriptionPersistencePort.java` com métodos `upsertFromSync(...)`, `findByTenantId(String)`
- Create: entities + repos em `infrastructure/.../billing/persistence/`
- Modify: usar `JdbcTemplate` ou repositório existente de tenant para atualizar `profile_level` só quando entitlement Stripe for fonte prioritária (documentar decisão).

Implementação textual mínima (sem duplicar 200 linhas neste plano): porta define:

```java
void upsertAndApplyProfileLevel(TenantSubscriptionSnapshot snapshot);
Optional<TenantSubscriptionSnapshot> findByTenantId(String tenantId);
```

O adaptador JDBC/JPA faz `UPSERT` em `tenant_subscription` e executa:

```sql
UPDATE tenant_configuration SET profile_level = :tier WHERE tenant_id = :tid
UPDATE portal_user SET profile_level = :tier WHERE tenant_id = :tid
```

(apenas onde `tier` ∈ {BASIC, PRO, ULTRA} conforme `BillingPlanTier.toProfileLevel().name()`).

- [ ] **Step 1:** Definir a porta na `application`.

- [ ] **Step 2:** Implementar adapter + teste de integração `@DataJpaTest` ou `@SpringBootTest` com Testcontainers Postgres se o projeto já usar.

Run (ajustar nome do teste criado):

```bash
mvn -q -pl infrastructure test -Dtest=TenantSubscriptionPersistenceAdapterTest
```

- [ ] **Step 3: Commit**

---

### Task 8: `BillingSubscriptionSyncService` — eventos §5.2 da spec (`checkout.session.completed`, `subscription.*`, `invoice.*`)

**Files:**

- Create: `application/src/main/java/com/atendimento/cerebro/application/service/billing/BillingSubscriptionSyncService.java`

Responsável por:

1. `StripeWebhookEventPersistencePort.tryMarkProcessed(eventId)` — se já processado, return.
2. Parse do `Event` → extrair subscription id → `Stripe.Subscription.retrieve(subId)` quando necessário.
3. Extrair `tenant_id` de `subscription.metadata`.
4. Mapear `priceId` usando `BillingStripeProperties.priceTierNonNull()`.
5. Construir `TenantSubscriptionSnapshot` e `upsertAndApplyProfileLevel`.

Cada método público cobre um tipo de evento; o controller apenas delega por `switch (event.getType())`.

- [ ] **Step 1:** Implementar caso `checkout.session.completed` e `customer.subscription.updated` primeiro (maior ROI).

Run:

```bash
mvn -q -pl application,infrastructure test
```

Expected: todos os testes verdes mais novos mocks.

- [ ] **Step 2: Commit**

```bash
git commit -am "feat(billing): subscription sync from stripe events"
```

---

### Task 9: REST Checkout + Billing Portal (dono apenas)

**Files:**

- Create: `BillingSessionController.java` em `infrastructure/.../billing/api/`

Corpo JSON:

```json
POST /v1/billing/checkout-session   { "tenantId": "acme", "priceId": "price_xxx" }
POST /v1/billing/portal-session     { "tenantId": "acme" }
```

Validação: Firebase UID actual == `stripe_customer.owner_firebase_uid` após migração (ou `portal_user.billing_owner` correspondente ao par `(tenant_id, firebase_uid)`).

Resposta `{ "url": "https://checkout.stripe.com/..." }`

Proteção: `.requestMatchers(HttpMethod.POST, "/v1/billing/checkout-session", "/v1/billing/portal-session").authenticated()` (confirmar contra `SecurityConfiguration`).

- [ ] **Step 1:** Implementar usando `StripeClient` / builders `SessionCreateParams` Stripe Java SDK 28.x (consultar snippets oficiais para subscrições).

Run teste manual com Postman JWT portal + Stripe test keys.

- [ ] **Step 2: Commit**

---

### Task 10: Filtro de entitlement §12.3 (`BillingEntitlementWebFilter`)

Paths **sempre** permitidos mesmo sem entitlement (além das já públicas na spec):

- `GET` landing estática (servida Next; `/v1/` pode não aplicar — limitar ao API se necessário)
- `/v1/auth/**` exceto onde produto definir diferente.
- `/v1/billing/**` (checkout, portal, webhook)
- `GET /health` ou actuator se existir

Para `/api/v1/**` utilizador autenticado **sem** assinatura válida → `403` com JSON `{ "error": "assinatura_inativa", "code": "BILLING_BLOCKED" }`.

- [ ] **Step 1:** Implementar filter depois da autenticação Firebase usando `TenantSubscriptionPersistencePort` + `TenantEntitlementEvaluator` + Clock injetável.

Run:

```bash
mvn -q -pl infrastructure test -Dtest=BillingEntitlementWebFilterTest
```

(caso de test MockMvc a criar com contexto limitado.)

- [ ] **Step 2: Commit**

---

### Task 11: Next.js — `pricing`, `billing/success`, `billing/suspended`, `middleware.ts`

**Files:**

- Create: `atendimento-frontEnd/atendimento-frontend/src/middleware.ts`
- Create: páginas em `src/app/[locale]/...`

**middleware.ts exemplo (adaptar aos paths reais do projecto):**

```typescript
import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const PUBLIC_PREFIXES = ["/pt-BR", "/en"]; // ajustar: locale + páginas públicas
const BILLING_PATHS = ["/pricing", "/billing/success", "/billing/suspended", "/login"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isBillingOrPublic =
    BILLING_PATHS.some((p) => pathname.includes(p)) ||
    pathname === "/" ||
    pathname.endsWith("/");
  // Se existirem cookies/sessão: chamar lightweight GET /api/.../billing-status ou campo em /auth/me
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
```

Integração firme: quando `/auth/me` expuser `billing: { blocked: boolean }`, o middleware redireciona para `/[locale]/billing/suspended`.

- [ ] **Step 1:** Páginas `pricing`: listar Basic/Pro/Ultra com toggle mensal/anual; botões chamam `POST /v1/billing/checkout-session` com o mesmo padrão de Bearer já usado em [`atendimento-frontEnd/atendimento-frontend/src/services/apiService.ts`](../../../atendimento-frontEnd/atendimento-frontend/src/services/apiService.ts).

- [ ] **Step 2:** `pnpm lint` / `npm run lint` conforme projeto

Run:

```bash
cd atendimento-frontEnd/atendimento-frontend
npm run build
```

Expected: `Compiled successfully`

- [ ] **Step 3: Commit**

---

## Self-review (checklist skill)

**1. Spec coverage**

| Req spec | Tarefas |
|-----------|---------|
| §4 Checkout + metadata tenant | Task 9 |
| §5 Webhooks + idempotência | Task 5, 8 + Task 7 `stripe_webhook_event` |
| §6 Estados + grace | Task 3 |
| §7 Modelo dados | Task 1, 7 |
| §12.3 Rotas livres incl. landing | Task 11 |
| Stripe como fonte de tier pago | Task 8, 7 (profile sync) |

**2. Placeholder scan:** Remover antes de mergir qualquer uso residual de strings `REPLACE` nos YAML — substituir por Price IDs reais do dashboard de testes. Ao completar a Task 8, substituir o corpo de `StripeValidatedWebhookSink.accept` por chamadas ao `BillingSubscriptionSyncService` (mantendo a classe ou renomeando para pipeline completo).

**3. Type consistency:** `BillingPlanTier` ↔ `tier` VARCHAR e `profile_level`; `stripe_status` sempre comparado em lower-case no evaluator onde aplicável — manter igual em sync service.

---

**Plan complete and saved to** `docs/superpowers/plans/2026-05-08-stripe-tenant-billing.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration. **REQUIRED SUB-SKILL:** `superpowers:subagent-driven-development`

2. **Inline execution** — run tasks in this session with `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
