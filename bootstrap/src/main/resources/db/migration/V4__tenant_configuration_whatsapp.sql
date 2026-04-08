ALTER TABLE tenant_configuration
    ADD COLUMN whatsapp_provider_type VARCHAR(32) NOT NULL DEFAULT 'SIMULATED'
        CONSTRAINT chk_whatsapp_provider_type CHECK (whatsapp_provider_type IN ('META', 'EVOLUTION', 'SIMULATED')),
    ADD COLUMN whatsapp_api_key TEXT NULL,
    ADD COLUMN whatsapp_instance_id VARCHAR(1024) NULL,
    ADD COLUMN whatsapp_base_url VARCHAR(2048) NULL;
