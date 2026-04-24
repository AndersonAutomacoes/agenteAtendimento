ALTER TABLE tenant_appointments
    ADD COLUMN reminder_sent BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN tenant_appointments.reminder_sent IS
    'true após envio do lembrete na véspera (job às 18h no fuso configurado).';

CREATE INDEX IF NOT EXISTS idx_tenant_appointments_reminder_pending
    ON tenant_appointments (booking_status, reminder_sent, starts_at)
    WHERE booking_status = 'AGENDADO' AND reminder_sent = false;
