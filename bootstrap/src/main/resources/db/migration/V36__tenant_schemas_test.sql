-- Schemas de teste para schema-per-tenant (Flyway continua a correr em public; dados por tenant podem ser migrados para estes schemas quando CEREBRO_MULTITENANCY_ENABLED=true).
CREATE SCHEMA IF NOT EXISTS oficina_ssa_01;
CREATE SCHEMA IF NOT EXISTS oficina_ssa_02;
