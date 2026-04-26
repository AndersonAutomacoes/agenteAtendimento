package com.atendimento.cerebro.infrastructure.adapter.out.email;

import com.atendimento.cerebro.application.port.out.InviteEmailSenderPort;
import com.atendimento.cerebro.infrastructure.config.InviteEmailProperties;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "cerebro.mail.invite", name = "enabled", havingValue = "true")
public class ResendInviteEmailSenderAdapter implements InviteEmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(ResendInviteEmailSenderAdapter.class);
    private static final DateTimeFormatter EXPIRES_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final InviteEmailProperties props;
    private final RestClient restClient;

    public ResendInviteEmailSenderAdapter(InviteEmailProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
    }

    @Override
    public void sendInviteEmail(InviteEmailCommand command) {
        validateConfig();
        String registrationUrl = props.getRegistrationUrl().strip();
        String fromName = props.getFromName() == null || props.getFromName().isBlank()
                ? "Equipe AxeZap"
                : props.getFromName().strip();
        String from = fromName + " <" + props.getFromEmail().strip() + ">";
        String subject = "Seu convite de acesso a plataforma AxeZap";
        String html = buildHtml(command, registrationUrl);
        String text = buildText(command, registrationUrl);
        Map<String, Object> payload =
                Map.of(
                        "from", from,
                        "to", new String[] {command.toEmail()},
                        "subject", subject,
                        "html", html,
                        "text", text);
        try {
            restClient
                    .post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + props.getResendApiKey().strip())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Forbidden ex) {
            String body = ex.getResponseBodyAsString();
            if (body != null
                    && body.toLowerCase().contains("domain is not verified")) {
                throw new IllegalStateException(
                        "Dominio de remetente nao verificado no Resend. Configure CEREBRO_MAIL_INVITE_FROM_EMAIL com um dominio verificado em https://resend.com/domains.",
                        ex);
            }
            if (body != null
                    && body.toLowerCase().contains("you can only send testing emails to your own email address")) {
                throw new IllegalStateException(
                        "Conta Resend em modo de teste: so e permitido enviar para o proprio e-mail da conta. Verifique um dominio em https://resend.com/domains para liberar envios para outros destinatarios.",
                        ex);
            }
            log.error("Resend retornou 403 ao enviar convite para tenant {}", command.tenantId(), ex);
            throw new IllegalStateException("Falha ao enviar e-mail de convite", ex);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar convite por e-mail via Resend para tenant {}", command.tenantId(), ex);
            throw new IllegalStateException("Falha ao enviar e-mail de convite", ex);
        }
    }

    private void validateConfig() {
        if (!"resend".equalsIgnoreCase(props.getProvider())) {
            throw new IllegalStateException("Provedor de e-mail não suportado: " + props.getProvider());
        }
        if (props.getResendApiKey() == null || props.getResendApiKey().isBlank()) {
            throw new IllegalStateException("cerebro.mail.invite.resend-api-key não configurado");
        }
        if (props.getFromEmail() == null || props.getFromEmail().isBlank()) {
            throw new IllegalStateException("cerebro.mail.invite.from-email não configurado");
        }
        if (props.getRegistrationUrl() == null || props.getRegistrationUrl().isBlank()) {
            throw new IllegalStateException("cerebro.mail.invite.registration-url não configurado");
        }
    }

    private String buildHtml(InviteEmailCommand command, String registrationUrl) {
        String businessName = command.establishmentName() == null || command.establishmentName().isBlank()
                ? command.tenantId()
                : command.establishmentName().strip();
        String expiresText = command.expiresAt() == null
                ? "Sem data de expiração definida."
                : "Valido ate " + EXPIRES_FORMAT.format(command.expiresAt()) + " (horario de Brasilia).";
        String supportLine = props.getSupportEmail() == null || props.getSupportEmail().isBlank()
                ? ""
                : "<p>Se precisar de ajuda, responda este e-mail ou escreva para " + escapeHtml(props.getSupportEmail().strip()) + ".</p>";
        return "<html><body style=\"font-family:Arial,sans-serif;background:#f5f7fb;padding:24px;color:#111827;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px;margin:0 auto;background:#ffffff;border-radius:12px;padding:24px;\">"
                + "<tr><td>"
                + "<h2 style=\"margin:0 0 12px 0;\">Seu acesso a plataforma AxeZap foi criado</h2>"
                + "<p>Voce recebeu este convite para acessar a conta <strong>" + escapeHtml(businessName) + "</strong>.</p>"
                + "<p><strong>Codigo de convite:</strong></p>"
                + "<p style=\"font-size:24px;font-weight:700;letter-spacing:1px;background:#f3f4f6;padding:12px;border-radius:8px;display:inline-block;\">"
                + escapeHtml(command.inviteCode())
                + "</p>"
                + "<h3 style=\"margin-top:24px;\">Passo a passo</h3>"
                + "<ol>"
                + "<li>Acesse a pagina de cadastro: <a href=\"" + escapeHtml(registrationUrl) + "\">" + escapeHtml(registrationUrl) + "</a>.</li>"
                + "<li>Crie sua conta ou faca login.</li>"
                + "<li>Informe o codigo de convite acima no campo solicitado.</li>"
                + "<li>Conclua o cadastro para liberar o acesso ao portal.</li>"
                + "</ol>"
                + "<p>Este codigo permite ate " + command.maxUses() + " uso(s). " + escapeHtml(expiresText) + "</p>"
                + "<p>Por seguranca, nao compartilhe este codigo com terceiros.</p>"
                + supportLine
                + "<p style=\"margin-top:24px;color:#6b7280;font-size:12px;\">Gestão de atendimento por AxeZap AI</p>"
                + "</td></tr></table></body></html>";
    }

    private String buildText(InviteEmailCommand command, String registrationUrl) {
        String businessName = command.establishmentName() == null || command.establishmentName().isBlank()
                ? command.tenantId()
                : command.establishmentName().strip();
        String expiresText = command.expiresAt() == null
                ? "Sem data de expiração definida."
                : "Valido ate " + EXPIRES_FORMAT.format(command.expiresAt()) + " (horario de Brasilia).";
        String supportLine = props.getSupportEmail() == null || props.getSupportEmail().isBlank()
                ? ""
                : "\nSuporte: " + props.getSupportEmail().strip();
        return "Seu acesso a plataforma AxeZap foi criado.\n\n"
                + "Conta: " + businessName + "\n"
                + "Codigo de convite: " + command.inviteCode() + "\n\n"
                + "Passo a passo:\n"
                + "1) Acesse " + registrationUrl + "\n"
                + "2) Crie sua conta ou faca login\n"
                + "3) Informe o codigo de convite\n"
                + "4) Conclua o cadastro para liberar o acesso\n\n"
                + "Uso maximo do codigo: " + command.maxUses() + "\n"
                + expiresText + "\n"
                + "Nao compartilhe este codigo com terceiros."
                + supportLine
                + "\n\nGestão de atendimento por AxeZap AI";
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
