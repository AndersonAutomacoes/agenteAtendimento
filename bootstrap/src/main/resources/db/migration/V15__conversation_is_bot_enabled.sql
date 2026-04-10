-- Alinha o nome da coluna ao contrato is_bot_enabled (padrão true na V14).
ALTER TABLE conversation RENAME COLUMN bot_enabled TO is_bot_enabled;
