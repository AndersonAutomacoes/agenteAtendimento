-- Origem da mensagem no contexto Gemini (USER=cliente, BOT=IA, HUMAN_ADMIN=operador no monitor).
ALTER TABLE conversation_message ADD COLUMN sender_type VARCHAR(32);

UPDATE conversation_message
SET sender_type = CASE role
    WHEN 'USER' THEN 'USER'
    WHEN 'ASSISTANT' THEN 'BOT'
    WHEN 'SYSTEM' THEN 'BOT'
    ELSE 'BOT'
END
WHERE sender_type IS NULL;

ALTER TABLE conversation_message ALTER COLUMN sender_type SET NOT NULL;

-- Após reativar a IA (enable-bot), a próxima resposta Gemini recebe instrução extra; consumido no primeiro chat.
ALTER TABLE conversation
    ADD COLUMN resume_ai_after_human BOOLEAN NOT NULL DEFAULT FALSE;
