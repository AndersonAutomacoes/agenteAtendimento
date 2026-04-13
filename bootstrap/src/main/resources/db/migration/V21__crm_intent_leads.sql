ALTER TABLE crm_customer
    ADD COLUMN last_intent VARCHAR(128) NULL,
    ADD COLUMN intent_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN last_intent_at TIMESTAMPTZ NULL;

CREATE INDEX idx_crm_customer_tenant_intent_status
    ON crm_customer (tenant_id, intent_status);

COMMENT ON COLUMN crm_customer.last_intent IS 'Última intenção principal (ex.: Orçamento, Agendamento) da classificação analytics.';
COMMENT ON COLUMN crm_customer.intent_status IS 'NONE, OPEN, PENDING_LEAD, ASSIGNED, CONVERTED, DISMISSED';
COMMENT ON COLUMN crm_customer.last_intent_at IS 'Quando a intenção foi registada; usado para a janela de 30 min.';
