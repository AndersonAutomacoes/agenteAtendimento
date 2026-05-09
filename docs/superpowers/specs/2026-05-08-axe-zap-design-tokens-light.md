# AxeZap frontend — design tokens (light system)

Este documento alinha‑se aos tokens definidos em `atendimento-frontend/src/app/globals.css`. Não usar hex aleatório em novo UI de produto: preferir estes semantic colors + `muted`/`border`/`primary`.

## Cores semânticas (Tailwind)

| Token | Utilitários típicos | Uso |
|--------|---------------------|-----|
| `success` | `text-success`, `border-success/40`, `bg-success/10`, `bg-success text-success-foreground` | Operação OK, estado configurado, confirmação |
| `warning` | `text-warning`, `border-warning/40`, `bg-warning/10` | Avise o utilizador, configuração incompleta, quotas |
| `info` | `text-info`, `border-info/40`, `bg-info/10` | Neutral/informativo, métricas de IA, simulado |
| `destructive` | já existente | Erro irreversível, eliminar |

`success-foreground`, `warning-foreground`, `info-foreground` existem para **superfícies plenas** (ex.: eventual botão ou chip preenchido), não obrigatórios em cada ecrã.

## Tipografia

Escala recomendada (Tailwind só):

1. Landing hero: `text-4xl`–`lg:text-[2.65rem]` + `font-bold` + `tracking-tight` + `text-balance`
2. Título de página na app: `text-2xl font-semibold tracking-tight`
3. Secção / cartão: `text-base`/`text-xl` `font-semibold` ou KPI `text-sm font-medium`
4. Corpo denso (tabela): `text-sm`; formulários onde o iOS faz zoom: `text-base`
5. Legenda / hint: `text-xs text-muted-foreground`
6. Números comparáveis: `tabular-nums`

Fontes: **Inter** (UI), **Geist Mono** (código/monoespaçado), **Lexend** (marca só na landing via `.font-landing-brand`).

## Evolução

Ao introduzir novos estados (“paid”, “pending billing”), primeiro tentar `success` / `warning` / `info`; só depois propor novo token aqui + em `globals.css`.
