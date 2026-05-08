---
name: find-skills
description: Ajuda usuários a descobrir e instalar skills de agente quando fazem perguntas como “como faço X”, “encontre uma skill para X”, “existe uma skill que...”, ou expressam interesse em estender capacidades. Use esta skill quando o usuário procurar funcionalidade que pode existir como skill instalável.
---

# Descobrir skills

Esta skill ajuda a descobrir e instalar skills do ecossistema aberto de skills para agentes.

## Quando usar esta skill

Use esta skill quando o usuário:

- Perguntar “como faço X”, onde X pode ser uma tarefa comum com skill existente
- Disse “ache uma skill para X” ou “existe uma skill para X”
- Perguntar “você pode fazer X”, onde X é uma capacidade especializada
- Expressar interesse em estender as capacidades do agente
- Quiser pesquisar ferramentas, modelos ou fluxos de trabalho
- Comentar que gostaria de ajuda em um domínio específico (design, testes, deploy, etc.)

## O que é o Skills CLI?

O Skills CLI (`npx skills`) é o gerenciador de pacotes do ecossistema aberto de skills para agentes. Skills são pacotes modulares que estendem as capacidades do agente com conhecimento especializado, fluxos de trabalho e ferramentas.

**Comandos principais:**

- `npx skills find [consulta]` — Busca skills de forma interativa ou por palavra-chave
- `npx skills add <pacote>` — Instala uma skill do GitHub ou de outras fontes
- `npx skills check` — Verifica atualizações das skills instaladas
- `npx skills update` — Atualiza todas as skills instaladas

**Explore skills em:** https://skills.sh/

## Como ajudar o usuário a encontrar skills

### Etapa 1: Entenda o que ele precisa

Quando um usuário pedir ajuda com algo, identifique:

1. O domínio (ex.: React, testes, design, deploy)
2. A tarefa específica (ex.: escrever testes, criar animações, revisar PRs)
3. Se a tarefa é comum o suficiente para provavelmente existir uma skill

### Etapa 2: Confira o leaderboard primeiro

Antes de rodar uma busca no CLI, veja o [leaderboard do skills.sh](https://skills.sh/) para checar se já existe uma skill conhecida para o domínio. O leaderboard classifica skills por total de instalações, destacando opções mais populares e consolidadas.

Por exemplo, skills em destaque para desenvolvimento web incluem:
- `vercel-labs/agent-skills` — React, Next.js, web design (100K+ instalações cada)
- `anthropics/skills` — Design de frontend, processamento de documentos (100K+ instalações)

### Etapa 3: Busque skills

Se o leaderboard não cobrir a necessidade do usuário, execute o comando de busca:

```bash
npx skills find [query]
```

Por exemplo:

- Usuário pergunta “como deixo meu app React mais rápido?” → `npx skills find react performance`
- Usuário pergunta “você pode me ajudar com revisão de PRs?” → `npx skills find pr review`
- Usuário diz “preciso criar um changelog” → `npx skills find changelog`

### Etapa 4: Verifique a qualidade antes de recomendar

**Não recomende uma skill só com base nos resultados da busca.** Sempre verifique:

1. **Número de instalações** — Prefira skills com 1K+ instalações. Seja cauteloso com valores abaixo de 100.
2. **Reputação da origem** — Fontes oficiais (`vercel-labs`, `anthropics`, `microsoft`) tendem a ser mais confiáveis que autores desconhecidos.
3. **Estrelas no GitHub** — Veja o repositório da fonte. Uma skill de um repositório com menos de 100 estrelas merece mais ceticismo.

### Etapa 5: Apresente opções ao usuário

Ao encontrar skills relevantes, apresente ao usuário:

1. O nome da skill e o que ela faz
2. O número de instalações e a origem
3. O comando de instalação que ele pode executar
4. Um link para saber mais no skills.sh

Exemplo de resposta:

```
Encontrei uma skill que pode ajudar! A skill "react-best-practices" traz
diretrizes de otimização de performance para React e Next.js da Vercel Engineering.
(185K instalações)

Para instalar:
npx skills add vercel-labs/agent-skills@react-best-practices

Saiba mais: https://skills.sh/vercel-labs/agent-skills/react-best-practices
```

### Etapa 6: Ofereça instalar

Se o usuário quiser seguir, você pode instalar a skill para ele:

```bash
npx skills add <owner/repo@skill> -g -y
```

A flag `-g` instala globalmente (nível de usuário) e `-y` pula confirmações interativas.

## Categorias comuns de skills

Ao buscar, considere estas categorias comuns:

| Categoria          | Exemplos de consultas                        |
| ------------------ | -------------------------------------------- |
| Desenvolvimento web| react, nextjs, typescript, css, tailwind     |
| Testes             | testing, jest, playwright, e2e                |
| DevOps             | deploy, docker, kubernetes, ci-cd           |
| Documentação       | docs, readme, changelog, api-docs           |
| Qualidade de código| review, lint, refactor, best-practices      |
| Design             | ui, ux, design-system, accessibility        |
| Produtividade      | workflow, automation, git                    |

## Dicas para buscas eficazes

1. **Use palavras-chave específicas**: “react testing” é melhor que só “testing”
2. **Tente termos alternativos**: Se “deploy” não funcionar, tente “deployment” ou “ci-cd”
3. **Confira fontes populares**: Muitas skills vêm de `vercel-labs/agent-skills` ou `ComposioHQ/awesome-claude-skills`

## Quando não houver skills

Se não existir skill relevante:

1. Reconheça que não foi encontrada skill existente
2. Ofereça ajudar com a tarefa diretamente com suas capacidades gerais
3. Sugira que o usuário pode criar a própria skill com `npx skills init`

Exemplo:

```
Busquei skills relacionadas a "xyz" mas não encontrei correspondências.
Ainda posso ajudar com essa tarefa diretamente! Quer que eu continue?

Se isso for algo que você faz com frequência, pode criar sua própria skill:
npx skills init my-xyz-skill
```
