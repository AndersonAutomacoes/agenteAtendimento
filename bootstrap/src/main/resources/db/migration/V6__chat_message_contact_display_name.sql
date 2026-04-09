ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS contact_display_name VARCHAR(512);
