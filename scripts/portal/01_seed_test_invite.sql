-- Convite de desenvolvimento para concluir POST /v1/auth/register (página /register).
-- Código em texto puro (o backend faz SHA-256): CEREBRO-TEST-ADMIN-INVITE
-- hash SHA-256: 6ecdd7c6a804653acce892f9b76bbc4c5bf0c4c9c977e99ffb7a98439fd9210c
--
-- Uso (tendo Postgres a correr):
--  a) Com psql no PATH: psql "postgresql://cerebro:cerebro@localhost:5433/cerebro" -f scripts/portal/01_seed_test_invite.sql
--  b) Sem psql (Windows, Docker): na raiz do repo: .\scripts\portal\run-seed-docker.ps1
--  c) Manual via container: Get-Content scripts\portal\01_seed_test_invite.sql -Raw | docker exec -i cerebro-postgres psql -U cerebro -d cerebro

INSERT INTO tenant_invite (id, tenant_id, code_hash, expires_at, max_uses, uses_count)
SELECT gen_random_uuid(),
       t.tenant_id,
       '6ecdd7c6a804653acce892f9b76bbc4c5bf0c4c9c977e99ffb7a98439fd9210c',
       NULL,
       100,
       0
FROM (SELECT tenant_id FROM tenant_configuration ORDER BY tenant_id LIMIT 1) AS t
ON CONFLICT (code_hash) DO NOTHING;
