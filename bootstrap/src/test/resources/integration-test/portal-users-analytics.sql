-- Tenants e utilizadores de portal para testes Camel Analytics (token: integration:<tenantId> → firebase_uid it-<tenantId>).
INSERT INTO tenant_configuration (tenant_id, system_prompt, whatsapp_provider_type, profile_level, portal_password_hash)
VALUES ('tenant-intents-api', '', 'SIMULATED', 'BASIC', NULL)
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO tenant_configuration (tenant_id, system_prompt, whatsapp_provider_type, profile_level, portal_password_hash)
VALUES ('tenant-chat-stats-api', '', 'SIMULATED', 'BASIC', NULL)
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO tenant_configuration (tenant_id, system_prompt, whatsapp_provider_type, profile_level, portal_password_hash)
VALUES ('tenant-chat-stats-range', '', 'SIMULATED', 'BASIC', NULL)
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO tenant_configuration (tenant_id, system_prompt, whatsapp_provider_type, profile_level, portal_password_hash)
VALUES ('tenant-x', '', 'SIMULATED', 'BASIC', NULL)
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO tenant_configuration (tenant_id, system_prompt, whatsapp_provider_type, profile_level, portal_password_hash)
VALUES ('t-export', '', 'SIMULATED', 'BASIC', NULL)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
VALUES ('a0000001-0001-4001-8001-000000000001', 'it-tenant-intents-api', 'tenant-intents-api', 'ULTRA')
ON CONFLICT (firebase_uid) DO NOTHING;
INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
VALUES ('a0000002-0002-4002-8002-000000000002', 'it-tenant-chat-stats-api', 'tenant-chat-stats-api', 'ULTRA')
ON CONFLICT (firebase_uid) DO NOTHING;
INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
VALUES ('a0000003-0003-4003-8003-000000000003', 'it-tenant-chat-stats-range', 'tenant-chat-stats-range', 'ULTRA')
ON CONFLICT (firebase_uid) DO NOTHING;
INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
VALUES ('a0000004-0004-4004-8004-000000000004', 'it-tenant-x', 'tenant-x', 'ULTRA')
ON CONFLICT (firebase_uid) DO NOTHING;
INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level)
VALUES ('a0000005-0005-4005-8005-000000000005', 'it-t-export', 't-export', 'ULTRA')
ON CONFLICT (firebase_uid) DO NOTHING;
