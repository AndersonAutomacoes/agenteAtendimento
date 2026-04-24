ALTER TABLE tenant_appointments
    ADD COLUMN notified_confirmation BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE tenant_appointments
    ADD COLUMN confirmation_message_id VARCHAR(512) NULL;

COMMENT ON COLUMN tenant_appointments.notified_confirmation IS
    'true após envio bem-sucedido da mensagem de confirmação ao cliente (WhatsApp).';
COMMENT ON COLUMN tenant_appointments.confirmation_message_id IS
    'messageId devolvido pela Evolution API no envio da confirmação; NULL se outro provider ou não devolvido.';
