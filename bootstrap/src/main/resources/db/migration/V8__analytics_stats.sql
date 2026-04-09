CREATE TABLE analytics_stats (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(512) NOT NULL,
    bucket_hour     TIMESTAMPTZ  NOT NULL,
    phone_number    VARCHAR(512) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    model_label     TEXT         NULL,
    classified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_analytics_stats_tenant_bucket_phone UNIQUE (tenant_id, bucket_hour, phone_number),
    CONSTRAINT ck_analytics_stats_category CHECK (category IN ('VENDAS', 'SUPORTE', 'FINANCEIRO', 'OUTRO'))
);

CREATE INDEX idx_analytics_stats_tenant_bucket ON analytics_stats (tenant_id, bucket_hour DESC);
