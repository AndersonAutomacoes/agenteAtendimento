---
name: Billing Stripe por tenant (Basic Pro Ultra)
overview: Plano pós-aprovação da spec docs/superpowers/specs/2026-05-08-stripe-tenant-billing-design.md — Stripe Checkout + webhooks idempotentes + persistência PostgreSQL (Flyway) + enforcement de entitlement no backend e no portal Next.js, com bloqueio total fora das rotas de exceção (landing, login, planos, portal, suspenso).
todos:
  - id: stripe-dashboard-env
    content: "Configurar Stripe (Products/Prices mensal+anual por tier, metadata tier, webhook endpoint de teste/prod) e variáveis STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, price IDs via config"
    status: pending
  - id: flyway-billing-schema
    content: "Migrações Flyway para stripe_customer, tenant_subscription e stripe_webhook_event + índices (tenant_id, stripe_subscription_id, customer_id)"
    status: pending
  - id: domain-app-billing-ports
    content: "Portas/use cases hexagonais (sincronizar subscription, resolver entitlement efetivo vs ausência, mapeamento price_id→tier+intervalo) sem depender do SDK Stripe no domain"
    status: pending
  - id: infra-stripe-adapters-webhook
    content: "Adaptador Stripe (Java Stripe SDK só em infrastructure), POST webhook com corpo bruto + validação Stripe-Signature, idempotência por event.id, handlers dos eventos da spec"
    status: pending
  - id: infra-checkout-portal-rest
    content: "REST para criar Checkout Session (tenant_id + price_id autorizado ao owner) e Billing Portal session; redirects e metadata client_reference_id alinhados à spec"
    status: pending
  - id: entitlement-guard-session
    content: "Integrar entitlement ao fluxo de sessão/perfil portal (substituir ou alinhar profileLevel/manual com dados de tenant_subscription); 403 ou redirect quando sem assinatura válida conforme §12.3"
    status: pending
  - id: fe-pricing-checkout-suspended
    content: "Next.js landing + página de planos (3 tiers pagos mensal/anual), sucesso pós-checkout, página conta suspensa; lista branca de rotas públicas alinhada à spec"
    status: pending
  - id: tests-stripe-cli
    content: "Testes de webhook (payloads/fixtures Stripe CLI ou stripe-mock), testes unitários do motor de entitlement (past_due grace, canceled com período active)"
    status: pending
isProject: false
---

# Implementação — billing Stripe por tenant

## Fonte da verdade

- **Spec (aprovada):** [`docs/superpowers/specs/2026-05-08-stripe-tenant-billing-design.md`](../../docs/superpowers/specs/2026-05-08-stripe-tenant-billing-design.md) — use como checklist de comportamento e tabela de eventos.

Premissas fixas já na spec:

- Basic, Pro e Ultra **pagos**; sem tier gratuito.
- Pagador = **dono do tenant**.
- Sem assinatura válida → **bloqueio total**, com exceções explícitas (landing, login, planos/checkout, portal, mensagem suspensa, webhooks backend).

Corrija este plano se a spec for alterada novamente.

## Contexto do monorepo (onde encaixar)

- **Backend:** Java 21, Spring Boot 3, Postgres + Flyway em [`bootstrap/src/main/resources/db/migration`](bootstrap/src/main/resources/db/migration), módulos `domain`, `application`, `infrastructure`, [`bootstrap`](bootstrap).
- **Portal:** Next.js em [`atendimento-frontEnd/atendimento-frontend`](atendimento-frontEnd/atendimento-frontend); auth Firebase e sessão descritos em plans anteriores (ex.: `/auth/me`, `SessionProfileSync`).

---

## Fase 0 — Stripe e configuração

1. Criar no Stripe **6 Prices** (Basic/Pro × monthly/yearly) ou equivalente + metadata `tier` em Price/Product.
2. Webhook único ou por ambiente com eventos da spec §5.2 (`checkout.session.completed`, `customer.subscription.*`, `invoice.paid`, `invoice.payment_failed`, opcionalmente `customer.updated`).
3. Propriedades Spring (ex.: `application.yml`): chave secreta, webhook secret, **mapa configurável** `price_id` → tier + intervalo (ou ler só metadata via API Stripe no sync — preferir config versionada para menos chamadas).

**Critério de pronto:** `stripe listen` ou dashboard dispara eventos de teste e o endpoint futuro responderá 2xx após implementação da Fase 3.

---

## Fase 1 — Persistência (Flyway)

Criar tabelas alinhadas à spec §7 (nomes podem snake_case SQL):

| Tabela | Observação |
|--------|------------|
| `stripe_customer` | `tenant_id` UNIQUE, `stripe_customer_id` UNIQUE, `owner_user_id` (string estável Firebase/sub) |
| `tenant_subscription` | espelho de status Stripe, `tier`, `price_id`, intervalo, períodos, `cancel_at_period_end` |
| `stripe_webhook_event` | `event_id` PK, tipo, timestamps, erro opcional |

Incluir colunas auxiliares se necessário: `grace_tracking` ou `billing_blocked_at` para política §6 + §12 item 1 (ex.: 7 dias `past_due`).

---

## Fase 2 — Domínio / aplicação (hexágono)

- **`TenantBillingSyncPort` / serviço de aplicação** que receba **DTOs já normalizados** (não objetos Stripe) para atualizar estado local.
- **`TenantEntitlementResolver`:** dado `tenantId`, devolve `{ hasAccess: boolean, tier?: enum, reason?: BLOCKED_NO_SUBSCRIPTION | PAST_DUE_GRACE | ... }` conforme máquina de estados §6 + grace §12.1 sugerido.
- Regras de **idempotência** e “reconcile subscription” ficam na aplicação; chamadas HTTP ao Stripe só em adapters.

Evitar Stripe SDK em `domain` e `application`.

---

## Fase 3 — Webhook (infra)

- Controller ou rota **sem** filtros que consumam body como JSON parse antes da verificação; usar **payload bruto**.
- Biblioteca oficial Stripe para `constructEvent` / equivalente Boot 3.
- Fluxo: validar → idempotência → despachar por `type` → atualizar BD em transação → 200 rápido (retries Stripe).
- Após eventos incompletos, **GET Subscription** na API Stripe quando a spec §5.1 exige reconciliação (`checkout.session.completed`).

Registrar **corrupção** de `tenant_id` (metadata inconsistente): log + falha segura sem alterar outro tenant.

---

## Fase 4 — Checkout e Customer Portal (REST interno/autenticado)

- **`POST …/billing/checkout-session`:** body `{ priceId, tenantId }` — validar que o usuário autenticado é **owner** do tenant.
- Stripe Checkout `mode=subscription`, `customer` existente ou criação `customer_creation` + persistência em `stripe_customer`; `success_url`, `cancel_url`; `subscription_data.metadata.tenant_id` + `client_reference_id`.
- **`POST …/billing/portal-session`:** apenas com `stripe_customer_id` associado ao tenant do caller.

Frontend consome apenas estes endpoints (URLs de Stripe retornadas no JSON).

---

## Fase 5 — Enforcement no backend portal

Para **todas** as rotas que hoje aplicam só `profileLevel`/`COMERCIAL` manual:

1. Resolver entitlement (DB local, eventual cache curto com invalidação por webhook).
2. Se `BLOCKED_*` segundo §12.3 → **403** com código estável ou redirect URL acordado com frontend.
3. Manter whitelist de endpoints **sem** checagem (health, webhook Stripe, eventual auth pública).

O backoffice interno pode **preservar** criação de tenant + perfil operacional até existir segunda spec “quem pode contornar billing” — default seguro **não** contornar produção pelo mesmo portal do cliente sem role explícito.

---

## Fase 6 — Frontend (Next.js)

1. **Landing** e marketing — já públicos (spec §12.3).
2. **`/pricing` ou `/planos`:** seleção mensal/anual + botões que chamam checkout-session com `priceId` configurado por env público ou fetch de catálogo mínimo.
3. **`/billing/success` ou query session_id:** apenas mensagem de aguarde webhook; opcional polling de `/auth/me` até tier atualizar.
4. **`/billing/suspended`:** copy alinhado a inadimplência/cancelamento; CTA Checkout + Portal.
5. **Middleware / guard de rotas `(app)`:** se sessão existe mas entitlement negado → redirect para página de cobrança; exceções alinhadas à lista da spec.

Sincronizar i18n com novas chaves onde necessário.

---

## Fase 7 — Testes e ferramentas

- **stripe-cli:** `stripe trigger …` contra ambiente dev com túnel.
- **Unitários:** matriz `(status Stripe, período, cancel_at_period_end, grace)` → `hasAccess`.
- **Integração webhook:** payload gravado uma vez como fixture (golden file) com secret de teste.

---

## Entrega incremental sugerida (PRs pequenos)

1. Migrações + entidades JPA/repositório (sem expor Stripe ainda).
2. Webhook + sync mínimo (só `customer.subscription.updated` + `checkout.session.completed`) + testes.
3. Checkout + Portal + FE planos + guard.
4. Completar eventos `invoice.*` + polish grace + métricas.

---

## Dependências Maven (referência)

- Adicionar `com.stripe:stripe-java` (versão atual compatível Java 21) **apenas** no módulo `infrastructure` (ou BOM central no parent `pom.xml` com escopo infra).

Confirmar política de licença/third-party na organização antes do merge.

---

## Riscos

- Desalinhamento entre **perfil backoffice manual** (`BASIC`/`PRO`…) e Stripe — definir uma **única fonte** para “entitlement de produção”: preferência **Stripe primeiro**, backoffice apenas suporte/forças menores documentadas.
- Webhook em ambiente escala horizontal: idempotência em BD obrigatória.
- Stripe test vs live keys em CI.

Quando iniciar código, marque cada `todos[].status` conforme Progress no Cursor até concluir o fluxo ponta-a-ponta.
