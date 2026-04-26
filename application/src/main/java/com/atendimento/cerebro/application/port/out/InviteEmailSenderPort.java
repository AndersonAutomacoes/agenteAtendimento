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
            Instant expiresAt) {}
}
