# Aplica 01_seed_test_invite.sql via psql *dentro* do container (não exige psql no Windows).
# Requer: docker compose com serviço postgres a correr (container cerebro-postgres).
$ErrorActionPreference = "Stop"
$container = "cerebro-postgres"
$sql = Join-Path $PSScriptRoot "01_seed_test_invite.sql"
if (-not (Test-Path $sql)) { throw "Ficheiro não encontrado: $sql" }
$running = docker ps -q -f "name=$container"
if (-not $running) {
    throw "Container '$container' não está a correr. Suba o Postgres: na raiz do repo, 'docker compose up -d postgres'"
}
Get-Content -Raw $sql | docker exec -i $container psql -U cerebro -d cerebro
Write-Host "Concluído. Convite: CEREBRO-TEST-ADMIN-INVITE" -ForegroundColor Green
