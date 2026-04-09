CREATE TABLE analytics_intents (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(512) NOT NULL,
    phone_number        VARCHAR(512) NOT NULL,
    primary_intent      VARCHAR(32)  NOT NULL,
    trigger_type        VARCHAR(32)  NOT NULL,
    turn_count          INT          NOT NULL DEFAULT 0,
    conversation_end_at TIMESTAMPTZ  NULL,
    model_label         TEXT         NULL,
    classified_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_analytics_intents_intent CHECK (primary_intent IN (
        'ORCAMENTO', 'AGENDAMENTO', 'DUVIDA_TECNICA', 'RECLAMACAO', 'OUTRO')),
    CONSTRAINT ck_analytics_intents_trigger CHECK (trigger_type IN (
        'MESSAGE_THRESHOLD', 'INACTIVITY_CLOSE'))
);

CREATE INDEX idx_analytics_intents_tenant_classified
    ON analytics_intents (tenant_id, classified_at DESC);

CREATE UNIQUE INDEX uq_analytics_intents_msg_threshold
    ON analytics_intents (tenant_id, phone_number, turn_count)
    WHERE trigger_type = 'MESSAGE_THRESHOLD';

CREATE UNIQUE INDEX uq_analytics_intents_inactivity
    ON analytics_intents (tenant_id, phone_number, conversation_end_at)
    WHERE trigger_type = 'INACTIVITY_CLOSE' AND conversation_end_at IS NOT NULL;
