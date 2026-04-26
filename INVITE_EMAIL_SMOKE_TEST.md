# Invite Email Smoke Test

## Environment Variables

Set these values in your `.env` (or environment):

```env
CEREBRO_MAIL_INVITE_ENABLED=true
CEREBRO_MAIL_INVITE_PROVIDER=resend
RESEND_API_KEY=re_xxxxxxxxxxxxxxxxx
CEREBRO_MAIL_INVITE_FROM_EMAIL=no-reply@seudominio.com
CEREBRO_MAIL_INVITE_FROM_NAME=Equipe AxeZap
CEREBRO_MAIL_INVITE_REGISTRATION_URL=http://localhost:3000/register
CEREBRO_MAIL_INVITE_SUPPORT_EMAIL=suporte@seudominio.com
```

## Quick End-to-End Validation

1. Start backend and frontend with the variables above.
2. Open `/internal/tenants`.
3. In `Clientes` tab, select a tenant and fill the invite recipient e-mail.
4. Click the invite generation button.
5. Confirm:
   - API returns success.
   - Invite e-mail is delivered.
   - E-mail includes invite code and registration instructions.
6. Open `/register`, use the received invite code, and finish registration.

## Negative Scenarios

1. Enter an invalid e-mail in the UI:
   - expected: front-end validation blocks submit.
2. Remove `RESEND_API_KEY` and retry:
   - expected: backend returns error and no e-mail is sent.
3. Keep `CEREBRO_MAIL_INVITE_ENABLED=false` and retry:
   - expected: invite e-mail sending path is disabled by configuration.

## Environment-Specific Registration URL

- Dev: `http://localhost:3000/register`
- Staging: `https://staging.seudominio.com/register`
- Production: `https://app.seudominio.com/register`
