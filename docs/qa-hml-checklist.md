# QA Report Template - HML (AxeZap)

- Data:
- Ambiente: HML
- URL painel: `http://165.227.194.76:3000`
- Build/API image:
- Build/Web image:
- Executor:
- Resultado final: `GO` / `NO-GO`

---

## 1) Smoke Tecnico

### 1.1 Health API
- [ ] Validado `GET /health`
- URL testada:
- Status:
- Evidencia (print/link):

### 1.2 Login Firebase
- [ ] Login realizado sem erro
- Usuario teste:
- Resultado:
- Evidencia (print/link):

### 1.3 Sessao `/auth/me`
- [ ] `GET /api/v1/auth/me` retornou sessao valida
- Status:
- Body resumido (`tenantId`, `profileLevel`):
- Evidencia (print/link):

---

## 2) Backoffice Interno

### 2.1 Gestao de Planos
- [ ] Alteracao de toggle salva
- [ ] Persistiu apos reload
- Endpoint:
- Status:
- Evidencia (print/link):

### 2.2 Criacao de Tenant
- [ ] Tenant criado
- [ ] Invite criado
- Tenant ID:
- Endpoint:
- Status:
- Evidencia (print/link):

### 2.3 Banco (validacao)
- [ ] Registro em `tenant_configuration`
- [ ] Registro em `tenant_invite`
- Query usada:
- Evidencia (print/link):

---

## 3) Configuracao WhatsApp

### 3.1 Salvar Tenant Settings
- [ ] `provider/base_url/instance/api_key` salvos
- [ ] Persistiu apos reabrir tela
- Endpoint:
- Status:
- Evidencia (print/link):

### 3.2 Evolution endpoint
- [ ] Endpoint de manager acessivel
- URL:
- Status:
- Evidencia (print/link):

---

## 4) Fluxo Ponta a Ponta

### 4.1 Webhook inbound
- [ ] Webhook recebido e processado
- Endpoint:
- Status:
- Evidencia (log/print):

### 4.2 Chat
- [ ] Mensagem enviada
- [ ] Resposta da IA recebida
- [ ] Sem erro 5xx/timeout
- Evidencia (print/link):

### 4.3 Monitoramento
- [ ] Conversa aparece no painel
- Evidencia (print/link):

---

## 5) Regressao Critica

### 5.1 Rewrites Next (`/api/...`)
- [ ] Sem `404` de Next.js
- Endpoints testados:
  - `/api/v1/internal/plans/config`
  - `/api/v1/internal/tenants`
  - `/api/v1/tenant/settings`
- Evidencia (print/link):

### 5.2 Firebase Admin backend
- [ ] Sem erro `Permission denied /run/firebase.json`
- [ ] Sem erro `firebaseApp` no startup
- Evidencia (log/link):

### 5.3 CORS
- [ ] Sem `Invalid CORS request`
- Origem testada:
- Evidencia (print/link):

---

## 6) Incidentes Encontrados

### INC-01
- Titulo:
- Severidade: `Blocker` / `Alta` / `Media` / `Baixa`
- Passos para reproduzir:
1.
2.
3.
- Resultado atual:
- Resultado esperado:
- Endpoint/Request:
- Status/Body:
- Evidencia (print/log):
- Observacao tecnica:

### INC-02
- Titulo:
- Severidade:
- Passos:
1.
2.
3.
- Resultado atual:
- Resultado esperado:
- Endpoint/Request:
- Status/Body:
- Evidencia:
- Observacao tecnica:

---

## 7) Decisao

- [ ] GO para continuidade
- [ ] NO-GO (bloqueado por incidentes)

### Justificativa final
<!-- Resumo executivo curto -->

### Acoes recomendadas
1.
2.
3.
