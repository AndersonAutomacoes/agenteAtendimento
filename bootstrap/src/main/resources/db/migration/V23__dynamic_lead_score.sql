-- Âncora da primeira classificação analytics (para contagem de mensagens USER posteriores).
ALTER TABLE chat_analytics
    ADD COLUMN first_analytics_at TIMESTAMPTZ NULL;

UPDATE chat_analytics
SET first_analytics_at = last_updated
WHERE first_analytics_at IS NULL;

COMMENT ON COLUMN chat_analytics.first_analytics_at IS 'Momento da primeira gravação analytics deste par tenant+telefone.';

-- Recalcular lead_score em crm_customer com base em chat_analytics + mensagens USER após first_analytics_at.
UPDATE crm_customer c
SET lead_score = LEAST(
        100,
        (CASE COALESCE(ca.main_intent, '')
             WHEN 'Orçamento' THEN 50
             WHEN 'Agendamento' THEN 80
             WHEN 'Suporte' THEN 10
             WHEN 'Outros' THEN 10
             WHEN 'Venda' THEN 40
             ELSE 10
         END)
        + 5 * COALESCE(
                (
                    SELECT COUNT(*)::int
                    FROM chat_message m
                    WHERE m.tenant_id = c.tenant_id
                      AND m.phone_number = ca.phone_number
                      AND m.role = 'USER'
                      AND m.occurred_at > COALESCE(ca.first_analytics_at, ca.last_updated)
                ),
                0
            )
    ),
    updated_at = NOW()
FROM chat_analytics ca
WHERE ca.tenant_id = c.tenant_id
  AND c.phone_number IS NOT NULL
  AND ca.phone_number = c.phone_number;
