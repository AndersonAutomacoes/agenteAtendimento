---
name: correcoes_estruturais_multi_tenant_audio_planos
overview: Implementar correções estruturais em backend e frontend para isolamento multi-tenant, transcrição de áudio no fluxo do agente, controle por funcionalidades de plano, validação estrita de serviços por tenant e limpeza de labels de ID da conta na UI.
todos:
  - id: harden-multitenant
    content: Fechar lacunas de autorização e centralizar tenantId em TenantContext (filtro/interceptor)
    status: completed
  - id: audio-transcription-flow
    content: Integrar transcrição de áudio com feedback imediato de processamento no WhatsApp
    status: completed
  - id: feature-access-control
    content: Implementar controle por funcionalidades com matriz dinâmica via banco/ConfigMap
    status: completed
  - id: service-id-enforcement
    content: Exigir service_id válido em tenant_services para concluir agendamento
    status: completed
  - id: ui-clean-accountid
    content: Remover labels de ID da Conta nas telas indicadas e limpar i18n
    status: completed
isProject: false
---

# Plano de implementação das correções estruturais

## Escopo confirmado
Implementar exatamente os 5 pontos solicitados: isolamento multi-tenant, transcrição de áudio antes do agente, acesso por funcionalidades (Basic/Pro/Ultra), validação de `service_id` em `tenant_services` para concluir agendamento e remoção visual de labels `ID da Conta`.

## 1) Blindagem multi-tenant (Dashboard, RAG, serviços)
- Endurecer autenticação/autorização nas rotas que hoje aceitam `tenantId` do request sem amarração ao tenant autenticado.
- Introduzir `TenantContext` global por requisição (via Filter/Interceptor + `ThreadLocal`) para resolver o `tenantId` autenticado uma única vez e reutilizar nos adapters/serviços.
- Em todas as rotas sensíveis, consumir `tenantId` do `TenantContext` (e não do payload/query para decisões de autorização), abortando quando houver inconsistência de identidade.
- Aplicar proteção uniforme em endpoints de conhecimento (RAG), mensagens, conversas, configurações e chat administrativo.

Arquivos-alvo principais:
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/KnowledgeBaseRestRoute.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/KnowledgeBaseRestRoute.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/IngestMultipartController.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/IngestMultipartController.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/MessagesRestRoute.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/MessagesRestRoute.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/ConversationsRestRoute.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/ConversationsRestRoute.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/TenantSettingsRestRoute.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/TenantSettingsRestRoute.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/BotSettingsController.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/BotSettingsController.java)

## 2) Fluxo de áudio -> transcrição -> ChatService
- Introduzir porta de aplicação para transcrição (ex.: `AudioTranscriptionPort`) e adapter de infraestrutura para provider de STT.
- Estender parser/route de webhook WhatsApp para identificar áudio inbound e transcrever antes de montar `ChatCommand`.
- Antes da transcrição, enviar reação/mensagem curta de status (ex.: `Processando seu áudio...`) para feedback imediato ao cliente no WhatsApp.
- Manter `ChatService` inalterado no contrato principal: receber texto transcrito em `userMessage` como mensagem comum.
- Incluir fallback de UX: mensagem curta de processamento + tratamento para transcrição vazia/inconclusiva pedindo repetição ou texto digitado.

Arquivos-alvo principais:
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/WhatsAppWebhookParser.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/WhatsAppWebhookParser.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/WhatsAppIntegrationRoute.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/inbound/rest/camel/WhatsAppIntegrationRoute.java)
- [D:/Documents/agenteAtendimento/application/src/main/java/com/atendimento/cerebro/application/dto/ChatCommand.java](D:/Documents/agenteAtendimento/application/src/main/java/com/atendimento/cerebro/application/dto/ChatCommand.java) (sem quebrar validação atual de texto)
- Novos arquivos em `application/port/out` e `infrastructure/adapter/out` para STT

## 3) Módulo de planos com Feature-based access control
- Evoluir de checagem por tier mínimo para matriz de funcionalidades por tenant/plano consultável dinamicamente.
- Introduzir enum/catálogo de features (ex.: `DASHBOARD_FULL`, `KNOWLEDGE_BASE_FULL`, `MONITORING_FULL`, etc.) e persistência por tenant/plano.
- Persistir a matriz em banco com fallback por `ConfigMap` (ou configuração externa equivalente), permitindo alterar permissões sem novo deploy.
- Adaptar backend para autorizar endpoints por feature requerida; manter compatibilidade com Basic/Pro/Ultra como baseline.
- Adaptar frontend para usar o mesmo modelo de feature gating (substituindo regras somente por tier nas telas/menu).

Arquivos-alvo principais:
- [D:/Documents/agenteAtendimento/domain/src/main/java/com/atendimento/cerebro/domain/tenant/ProfileLevel.java](D:/Documents/agenteAtendimento/domain/src/main/java/com/atendimento/cerebro/domain/tenant/ProfileLevel.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/ProfileTierAuthorizationManager.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/ProfileTierAuthorizationManager.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/security/SecurityConfiguration.java)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/plan/feature-guard.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/plan/feature-guard.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/layout/nav-config.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/layout/nav-config.tsx)

## 4) Normalização de serviços no agendamento (`service_id` obrigatório)
- Criar entidade/tabela de serviços por tenant e garantir vínculo do agendamento a `service_id` válido do mesmo tenant.
- Ajustar fluxo de criação/validação para bloquear confirmação quando serviço não existe na `tenant_services` do tenant.
- Atualizar persistência de agendamento para armazenar `service_id` (e manter nome derivado para exibição quando necessário).
- Ajustar prompts/fluxo de confirmação para listar somente serviços válidos do tenant.

Arquivos-alvo principais:
- [D:/Documents/agenteAtendimento/application/src/main/java/com/atendimento/cerebro/application/service/AppointmentService.java](D:/Documents/agenteAtendimento/application/src/main/java/com/atendimento/cerebro/application/service/AppointmentService.java)
- [D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/out/ai/GeminiSchedulingTools.java](D:/Documents/agenteAtendimento/infrastructure/src/main/java/com/atendimento/cerebro/infrastructure/adapter/out/ai/GeminiSchedulingTools.java)
- Migrações em [D:/Documents/agenteAtendimento/bootstrap/src/main/resources/db/migration](D:/Documents/agenteAtendimento/bootstrap/src/main/resources/db/migration)
- Repositórios JDBC de appointments/services no módulo `infrastructure/adapter/out/persistence`

## 5) UI Clean (remoção de "ID da Conta")
- Remover rendering do label e valor de `ID da Conta` nas telas indicadas (Agendamentos, Monitoramento, Chat de Teste, Base de Conhecimento e Dashboard).
- Limpar chaves i18n não utilizadas relacionadas a `accountId`.

Arquivos-alvo principais:
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/dashboard/dashboard-panel.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/dashboard/dashboard-panel.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/dashboard/appointments/page.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/dashboard/appointments/page.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/dashboard/monitoramento/page.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/dashboard/monitoramento/page.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/test-chat/page.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/test-chat/page.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/knowledge/page.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/(app)/knowledge/page.tsx)
- [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/pt-BR.json](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/pt-BR.json)

## Ordem de execução sugerida
1. Multi-tenant security hardening (reduz risco imediato de vazamento).
2. Normalização de `tenant_services` + `service_id` (garante integridade de negócio).
3. Fluxo de áudio com transcrição para texto.
4. Feature-based access control para planos.
5. UI clean + ajustes i18n finais.

## Critérios de validação
- `TenantContext` é preenchido e limpo por requisição; endpoints sensíveis não dependem de `tenantId` informado manualmente no request para autorização.
- Áudio inbound envia feedback imediato de processamento, depois transcreve e chega ao `ChatService` como texto; falha de STT gera fallback amigável.
- Endpoints/telas respeitam matriz de features por plano (Basic/Pro/Ultra) carregada dinamicamente de banco/ConfigMap, sem necessidade de deploy para ajustes de permissão.
- Agendamento só confirma quando `service_id` existe em `tenant_services` do tenant.
- Labels `ID da Conta` não aparecem mais nas telas solicitadas.
