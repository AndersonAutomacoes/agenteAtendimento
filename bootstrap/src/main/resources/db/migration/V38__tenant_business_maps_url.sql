ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS business_maps_url TEXT;

COMMENT ON COLUMN tenant_configuration.business_maps_url IS 'Link Google Maps do estabelecimento (card de confirmação de agendamento no WhatsApp)';
