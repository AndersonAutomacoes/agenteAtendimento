ALTER TABLE analytics_intents
    ADD COLUMN conversation_sentiment VARCHAR(16) NULL;

ALTER TABLE analytics_intents DROP CONSTRAINT IF EXISTS ck_analytics_intents_sentiment;
ALTER TABLE analytics_intents
    ADD CONSTRAINT ck_analytics_intents_sentiment
        CHECK (
            conversation_sentiment IS NULL
                OR conversation_sentiment IN ('POSITIVO', 'NEUTRO', 'NEGATIVO')
            );
