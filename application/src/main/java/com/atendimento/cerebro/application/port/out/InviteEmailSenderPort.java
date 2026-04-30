package com.atendimento.cerebro.application.port.out;

import java.time.Instant;

public interface InviteEmailSenderPort {

    void sendInviteEmail(InviteEmailCommand command);

    record InviteEmailCommand(
            String toEmail,
            String tenantId,
            String establishmentName,
            String inviteCode,
            int maxUses,
            Instant expiresAt,
            /** Conteúdo para {@code img} src ou só base64; {@code null} omite QR. */
            String whatsappPairingQrDataUriOrPlainBase64,
            /** Linha opcional HTML/texto plano extra (avisos provisioning, etc.). */
            String whatsappPairingNotePlain) {}
}
