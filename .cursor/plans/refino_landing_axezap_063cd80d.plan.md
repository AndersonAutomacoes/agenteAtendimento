---
name: Refino landing AxeZap
overview: Refinar a landing com polimento visual premium, reforço de prova social, simplificação do formulário e nova seção de integrações, mantendo consistência com i18n e validação da API.
todos:
  - id: landing-visual-polish
    content: Adicionar mesh gradient animado, glow hover nos cards e tipografia diferenciada no logo do menu
    status: completed
  - id: social-proof-card
    content: Reestruturar depoimento em card centralizado com avatar e 5 estrelas
    status: completed
  - id: form-simplification
    content: Reduzir formulário para nome + WhatsApp + consentimento e trocar texto do botão CTA
    status: completed
  - id: api-contract-update
    content: Ajustar rota /api/contact para novo payload sem email/message e manter validações essenciais
    status: completed
  - id: integrations-section
    content: Criar seção Integrações com logos em cinza e colorização no hover + textos i18n
    status: completed
  - id: quality-check
    content: Executar verificação de lint/build e validar comportamento visual responsivo
    status: completed
isProject: false
---

# Plano de ajustes da Landing Page AxeZap

## Escopo confirmado
- Simplificar formulário para **Nome + WhatsApp**, mantendo **consentimento**.
- Ajustar API para aceitar o novo payload sem `email` e `message`.

## Implementação proposta

### 1) Polimento visual e UI
- Atualizar o container principal e hero em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx) para incluir um **mesh gradient animado sutil** no fundo (com fallback para `prefers-reduced-motion`).
- Melhorar os cards de benefícios em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx) com **borda glow no hover** (efeito leve, sem excesso de brilho).
- Aplicar tipografia distinta no logo “AxeZap” do topo no mesmo arquivo, usando variação da família já carregada (Inter) e/ou adicionar Lexend no layout em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/layout.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/layout.tsx).
- Incluir suporte a `favicon.ico`: criar o arquivo em `public/` e registrar em metadata de [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/layout.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/[locale]/layout.tsx).

### 2) Prova social mais forte
- Reestruturar a seção de depoimento em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx) para um **card centralizado** com:
  - avatar genérico (ícone de usuário),
  - linha de **5 estrelas**,
  - quote e atribuição em hierarquia visual mais clara.

### 3) Formulário de conversão (front + API)
- Ajustar UI e estado em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-contact-form.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-contact-form.tsx):
  - remover campos de e-mail e mensagem,
  - manter nome, WhatsApp e consentimento,
  - alterar CTA para **“Quero escalar meu negócio”**.
- Adaptar validação/persistência em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/api/contact/route.ts](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/app/api/contact/route.ts) para aceitar o novo contrato sem rejeição por campos removidos.
- Revisar textos i18n de formulário e CTA em:
  - [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/pt-BR.json](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/pt-BR.json)
  - [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/en.json](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/en.json)
  - [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/es.json](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/es.json)
  - [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/zh-CN.json](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/messages/zh-CN.json)

### 4) Nova seção “Integrações”
- Inserir seção entre blocos estratégicos em [D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx](D:/Documents/agenteAtendimento/atendimento-frontEnd/atendimento-frontend/src/components/landing/landing-page-client.tsx), com logos/ícones de WhatsApp, Google Calendar e PostgreSQL.
- Aplicar estilo “cinza -> colorido no hover” com transição suave e acessível (contraste/legibilidade adequados em dark mode).
- Adicionar textos da seção ao i18n nos mesmos arquivos de mensagens.

## Verificação
- Validar tipagem e build local da aplicação frontend.
- Revisar lints dos arquivos alterados.
- Conferir experiência visual em desktop/mobile e com `prefers-reduced-motion` ativo.