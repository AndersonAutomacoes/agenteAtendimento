@echo off
REM Teste E2E do ChatService (PGVector + mock IA) contra o Postgres do docker-compose.
REM Suba antes: docker compose up -d
REM -Dtest ignora os excludes do pom.xml e executa de fato os *LocalPostgresTest (inclui rotas Camel).
setlocal
cd /d "%~dp0"
set CEREBRO_IT_USE_LOCAL_PG=true
call mvnw.cmd -pl bootstrap -am test "-Dtest=ChatServiceIntegrationTest,ChatServiceIntegrationLocalPostgresTest,CerebroApplicationLocalPostgresTest,ChatRestRouteIntegrationLocalPostgresTest,ChatRestRouteTimeoutIntegrationLocalPostgresTest" -Pchat-it
exit /b %ERRORLEVEL%
