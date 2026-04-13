-- Recalcular lead_score com a matriz atual (LeadScoringService): base por intenção + 5 pts por troca USER→ASSISTANT
-- após COALESCE(first_analytics_at, last_updated), teto 100.
UPDATE crm_customer c
SET lead_score = sub.score,
    updated_at = NOW()
FROM (
         SELECT ca.tenant_id,
                ca.phone_number,
                LEAST(
                        100,
                        (
                            CASE ca.main_intent
                                WHEN 'Agendamento' THEN (
                                    CASE
                                        WHEN COALESCE(ec.exchanges, 0) >= 2 THEN 95
                                        ELSE 85
                                        END
                                    )
                                WHEN 'Orçamento' THEN 50
                                WHEN 'Suporte' THEN 10
                                WHEN 'Outros' THEN 10
                                WHEN 'Venda' THEN 35
                                ELSE 10
                                END
                            ) + 5 * COALESCE(ec.exchanges, 0)
                ) AS score
         FROM chat_analytics ca
                  LEFT JOIN LATERAL (
             SELECT COUNT(*)::bigint AS exchanges
             FROM (
                      SELECT m.role,
                             LAG(m.role) OVER (ORDER BY m.occurred_at ASC, m.id ASC) AS prev_role
                      FROM chat_message m
                      WHERE m.tenant_id = ca.tenant_id
                        AND m.phone_number = ca.phone_number
                        AND m.occurred_at > COALESCE(ca.first_analytics_at, ca.last_updated)
                  ) w
             WHERE w.role = 'ASSISTANT'
               AND w.prev_role = 'USER'
             ) ec ON TRUE
     ) sub
WHERE c.phone_number IS NOT NULL
  AND sub.tenant_id = c.tenant_id
  AND sub.phone_number = c.phone_number;

COMMENT ON COLUMN crm_customer.lead_score IS '0–100: peso por main_intent em chat_analytics + 5 por troca USER→ASSISTANT após primeira analytics.';
