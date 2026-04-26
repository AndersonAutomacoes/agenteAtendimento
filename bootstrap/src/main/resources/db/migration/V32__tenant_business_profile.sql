-- Perfil comercial, agendamento padrão, adimplência, calendário/planilha, número WhatsApp de contacto
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS establishment_name         TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS business_address           TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS opening_hours              TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS business_contacts          TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS business_facilities        TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS default_appointment_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS billing_compliant         BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS calendar_access_notes      TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS google_spreadsheet_url     TEXT;
ALTER TABLE tenant_configuration
    ADD COLUMN IF NOT EXISTS whatsapp_business_number   TEXT;

COMMENT ON COLUMN tenant_configuration.establishment_name IS 'Nome do estabelecimento (negócio)';
COMMENT ON COLUMN tenant_configuration.business_address IS 'Endereço';
COMMENT ON COLUMN tenant_configuration.opening_hours IS 'Horário de funcionamento (texto livre)';
COMMENT ON COLUMN tenant_configuration.business_contacts IS 'Contatos (telefone, e-mail, etc.)';
COMMENT ON COLUMN tenant_configuration.business_facilities IS 'Facilidades / diferenciais';
COMMENT ON COLUMN tenant_configuration.default_appointment_minutes IS 'Duração padrão de cada atendimento (minutos)';
COMMENT ON COLUMN tenant_configuration.billing_compliant IS 'Adimplente com o plano (gestão comercial/manual)';
COMMENT ON COLUMN tenant_configuration.calendar_access_notes IS 'Notas de acesso ao calendário Google (credenciais / instruções internas)';
COMMENT ON COLUMN tenant_configuration.google_spreadsheet_url IS 'URL da planilha Google associada, se houver';
COMMENT ON COLUMN tenant_configuration.whatsapp_business_number IS 'Número de WhatsApp comercial a apresentar / contacto';
