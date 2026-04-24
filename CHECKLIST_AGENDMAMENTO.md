# Checklist Rapido de Regressao (Agendamento)

Use este roteiro sempre que mexer no fluxo de agendamento/reagendamento/cancelamento.

## Cenarios essenciais

- [] Novo agendamento basico: pedir horario livre, escolher opcao, confirmar; deve criar 1 evento e enviar so confirmacao final ao cliente.
- [] Reagendamento "de X para Y" disponivel: ex. `amanha das 11:00 para as 12:00`; deve cancelar 11:00 e criar 12:00 (nunca manter os dois).
- [] Reagendamento "de X para Y" indisponivel: se Y ocupado, deve manter X, informar indisponibilidade e oferecer lista de horarios.
- [] Sem agendamentos ativos: apos "sem agendamentos ativos", `sim/ok` deve voltar para fluxo de novo agendamento (nao repetir cancelamento).
- [] Confirmacoes curtas com contexto: `ok/sim` so confirmar quando houver contexto real (lista de slots, draft de confirmacao, cancel list).
- [] Confirmacoes curtas sem contexto: `ok/sim` apos encerramento ("se precisar, so chamar") nao deve disparar agendamento/cancelamento.
- [] Nao vazar mensagem tecnica: nunca enviar ao cliente textos internos como "cliente recebeu confirmacao automatica...".
- [] Cancelamento por lista: `listar agendamentos` -> escolher `opcao N`/ID; deve cancelar o item certo.
- [] Idempotencia de cancelamento: cancelar item ja cancelado deve responder de forma segura sem erro e sem efeitos colaterais.
- [] Consistencia final (fonte de verdade): validar no calendario + banco que ficou so o estado final esperado.

## Comandos rapidos (opcional)

- Testes focados:
  - `.\mvnw.cmd -q -pl application,infrastructure test "-Dtest=ChatServiceTest,AppointmentServiceTest,SchedulingUserReplyNormalizerTest,GeminiChatEngineAdapterSchedulingIntentTest" -DfailIfNoTests=false`
- Rebuild API local:
  - `docker compose up -d --build api`
- Logs ao vivo:
  - `docker compose logs -f api`
