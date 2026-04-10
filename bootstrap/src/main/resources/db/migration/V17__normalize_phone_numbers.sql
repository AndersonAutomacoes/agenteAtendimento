-- Normaliza identificadores de telefone para apenas dígitos quando existirem (alinhado a
-- ConversationId wa-<dígitos> e JdbcConversationBotStateRepository.canonicalPhoneKey).
-- Caso contrário mantém btrim(phone_number) para não perder valores sem dígitos.
-- Tabelas com UNIQUE (tenant_id, phone_number): reconstrói ou deduplica após o UPDATE.

-- =============================================================================
-- conversation: agrega linhas que colapsam para o mesmo (tenant_id, dígitos).
-- =============================================================================
CREATE TEMP TABLE _conv_norm AS
SELECT tenant_id,
       CASE
           WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 0
               THEN regexp_replace(phone_number, '[^0-9]', '', 'g')
           ELSE btrim(phone_number)
       END AS phone_key,
       is_bot_enabled,
       resume_ai_after_human,
       updated_at
FROM conversation;

CREATE TEMP TABLE _conv_merged AS
SELECT tenant_id,
       phone_key,
       (ARRAY_AGG(is_bot_enabled ORDER BY updated_at DESC NULLS LAST))[1] AS is_bot_enabled,
       bool_or(resume_ai_after_human)                                        AS resume_ai_after_human,
       max(updated_at)                                                       AS updated_at
FROM _conv_norm
GROUP BY tenant_id, phone_key;

DELETE FROM conversation;

INSERT INTO conversation (tenant_id, phone_number, is_bot_enabled, resume_ai_after_human, updated_at)
SELECT tenant_id, phone_key, is_bot_enabled, resume_ai_after_human, updated_at
FROM _conv_merged;

DROP TABLE _conv_norm;
DROP TABLE _conv_merged;

-- =============================================================================
-- conversation_message: sufixo de wa-* só com dígitos.
-- =============================================================================
UPDATE conversation_message
SET conversation_id =
        'wa-' || regexp_replace(substring(conversation_id from 4), '[^0-9]', '', 'g')
WHERE conversation_id LIKE 'wa-%'
  AND length(regexp_replace(substring(conversation_id from 4), '[^0-9]', '', 'g')) > 0;

-- =============================================================================
-- chat_message: uma linha por mensagem; sem UNIQUE em phone.
-- =============================================================================
UPDATE chat_message
SET phone_number =
        CASE
            WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 0
                THEN regexp_replace(phone_number, '[^0-9]', '', 'g')
            ELSE btrim(phone_number)
        END
WHERE phone_number IS NOT NULL;

-- =============================================================================
-- chat_analytics: UNIQUE (tenant_id, phone_number) — remove duplicados pós-normalização.
-- =============================================================================
UPDATE chat_analytics
SET phone_number =
        CASE
            WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 0
                THEN regexp_replace(phone_number, '[^0-9]', '', 'g')
            ELSE btrim(phone_number)
        END;

DELETE
FROM chat_analytics a
WHERE a.ctid IN (SELECT ctid
                 FROM (SELECT ctid,
                              row_number() OVER (
                                  PARTITION BY tenant_id, phone_number
                                  ORDER BY last_updated DESC NULLS LAST, id DESC
                                  ) AS rn
                       FROM chat_analytics) x
                 WHERE rn > 1);

-- =============================================================================
-- analytics_intents: índices únicos parciais em phone — deduplica após UPDATE.
-- =============================================================================
UPDATE analytics_intents
SET phone_number =
        CASE
            WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 0
                THEN regexp_replace(phone_number, '[^0-9]', '', 'g')
            ELSE btrim(phone_number)
        END;

DELETE
FROM analytics_intents a
         USING analytics_intents b
WHERE a.id > b.id
  AND a.tenant_id = b.tenant_id
  AND a.phone_number = b.phone_number
  AND a.turn_count = b.turn_count
  AND a.trigger_type = 'MESSAGE_THRESHOLD'
  AND b.trigger_type = 'MESSAGE_THRESHOLD';

DELETE
FROM analytics_intents a
         USING analytics_intents b
WHERE a.id > b.id
  AND a.tenant_id = b.tenant_id
  AND a.phone_number = b.phone_number
  AND a.conversation_end_at IS NOT DISTINCT FROM b.conversation_end_at
  AND a.trigger_type = 'INACTIVITY_CLOSE'
  AND b.trigger_type = 'INACTIVITY_CLOSE';

-- =============================================================================
-- analytics_stats: UNIQUE (tenant_id, bucket_hour, phone_number)
-- =============================================================================
UPDATE analytics_stats
SET phone_number =
        CASE
            WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) > 0
                THEN regexp_replace(phone_number, '[^0-9]', '', 'g')
            ELSE btrim(phone_number)
        END;

DELETE
FROM analytics_stats a
WHERE a.ctid IN (SELECT ctid
                 FROM (SELECT ctid,
                              row_number() OVER (
                                  PARTITION BY tenant_id, bucket_hour, phone_number
                                  ORDER BY classified_at DESC NULLS LAST, id DESC
                                  ) AS rn
                       FROM analytics_stats) x
                 WHERE rn > 1);
