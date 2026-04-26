package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.dto.AppointmentReminderCandidate;
import com.atendimento.cerebro.application.dto.WhatsAppTextPayload;
import com.atendimento.cerebro.application.dto.WhatsAppTextSendResult;
import com.atendimento.cerebro.application.port.out.WhatsAppTextOutboundPort;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Monta o texto do lembrete (véspera) e envia pelo mesmo canal outbound das confirmações (Evolution/Meta/simulado).
 */
public class AppointmentReminderNotificationService {

    private static final DateTimeFormatter DATE_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_BR = DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppTextOutboundPort whatsAppTextOutboundPort;

    public AppointmentReminderNotificationService(WhatsAppTextOutboundPort whatsAppTextOutboundPort) {
        this.whatsAppTextOutboundPort = whatsAppTextOutboundPort;
    }

    public WhatsAppTextSendResult sendReminder(AppointmentReminderCandidate app, ZoneId displayZone) {
        String raw = CrmConversationSupport.phoneDigitsOnlyFromConversationId(app.conversationId()).orElse("");
        String number = CrmConversationSupport.digitsForEvolutionApi(raw);
        if (number.isEmpty()) {
            return WhatsAppTextSendResult.fail("telefone indisponível (conversation_id não é WhatsApp ou está vazio)");
        }

        LocalDateTime ldt = LocalDateTime.ofInstant(app.startsAt(), displayZone);
        String date = ldt.format(DATE_BR);
        String time = ldt.format(TIME_BR);
        String clientName = app.clientName() != null && !app.clientName().isBlank() ? app.clientName().strip() : "Cliente";

        String text =
                """
                        🔔 Lembrete de Agendamento

                        Olá, %s! Passando para lembrar que temos um encontro marcado amanhã na nossa oficina:

                        📅 Data: %s
                        ⏰ Horário: %s

                        Caso precise ajustar algo, me avise agora! Se estiver tudo certo, te esperamos aqui. 🛠️🚗

                        Gestão de atendimento por AxeZap AI
                        """
                        .stripIndent()
                        .formatted(clientName, date, time);

        return whatsAppTextOutboundPort.sendText(new WhatsAppTextPayload(app.tenantId(), number, text));
    }
}
