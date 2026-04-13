ALTER TABLE crm_customer
    ADD COLUMN last_detected_intent VARCHAR(128) NULL,
    ADD COLUMN lead_score SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN is_converted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE crm_customer
    ADD CONSTRAINT crm_customer_lead_score_range CHECK (lead_score >= 0 AND lead_score <= 100);

UPDATE crm_customer
SET last_detected_intent = COALESCE(NULLIF(TRIM(last_detected_intent), ''), last_intent);

UPDATE crm_customer
SET is_converted = (intent_status = 'CONVERTED' OR total_appointments > 0);

COMMENT ON COLUMN crm_customer.last_detected_intent IS 'Última intenção detetada pela IA (ex.: Orçamento, Agendamento).';
COMMENT ON COLUMN crm_customer.lead_score IS 'Pontuação 0–100 conforme urgência da intenção.';
COMMENT ON COLUMN crm_customer.is_converted IS 'true após agendamento registado em tenant_appointments.';
COMMENT ON COLUMN crm_customer.intent_status IS 'NONE, OPEN, PENDING_LEAD, HOT_LEAD, ASSIGNED, CONVERTED, DISMISSED';

CREATE INDEX idx_crm_customer_tenant_sales_opp
    ON crm_customer (tenant_id, intent_status, lead_score DESC);
