CREATE TABLE chat_analytics (
    id              BIGSERIAL PRIMARY KEY,
    phone_number    VARCHAR(512) NOT NULL,
    tenant_id       VARCHAR(512) NOT NULL,
    main_intent     VARCHAR(32)  NOT NULL,
    sentiment       VARCHAR(16)  NOT NULL,
    last_updated    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_chat_analytics_main_intent CHECK (main_intent IN (
        'Venda', 'Suporte', 'Orçamento', 'Agendamento', 'Outros')),
    CONSTRAINT ck_chat_analytics_sentiment CHECK (sentiment IN (
        'Positivo', 'Neutro', 'Negativo'))
);

CREATE UNIQUE INDEX uq_chat_analytics_tenant_phone
    ON chat_analytics (tenant_id, phone_number);

CREATE INDEX idx_chat_analytics_tenant
    ON chat_analytics (tenant_id);
