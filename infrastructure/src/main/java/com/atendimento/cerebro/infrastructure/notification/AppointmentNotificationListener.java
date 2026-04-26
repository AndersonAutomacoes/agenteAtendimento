package com.atendimento.cerebro.infrastructure.notification;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.dto.WhatsAppTextPayload;
import com.atendimento.cerebro.application.logging.AppointmentAuditLog;
import com.atendimento.cerebro.application.event.AppointmentCancelledEvent;
import com.atendimento.cerebro.application.event.AppointmentConfirmedEvent;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.WhatsAppTextOutboundPort;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationCardFormatter;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroAppointmentConfirmationProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Notificações assíncronas pós-agendamento e pós-cancelamento via {@code direct:processWhatsAppResponse} (Evolution /
 * Meta / simulado).
 */
@Component
public class AppointmentNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private static final DateTimeFormatter DATE_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter TIME_BR = DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppTextOutboundPort whatsAppTextOutboundPort;
    private final TenantAppointmentStorePort appointmentStore;
    private final CerebroAppointmentConfirmationProperties appointmentConfirmationProperties;

    public AppointmentNotificationListener(
            WhatsAppTextOutboundPort whatsAppTextOutboundPort,
            TenantAppointmentStorePort appointmentStore,
            CerebroAppointmentConfirmationProperties appointmentConfirmationProperties) {
        this.whatsAppTextOutboundPort = whatsAppTextOutboundPort;
        this.appointmentStore = appointmentStore;
        this.appointmentConfirmationProperties = appointmentConfirmationProperties;
    }

    @Async
    @EventListener
    public void onAppointmentConfirmed(AppointmentConfirmedEvent event) {
        Long appointmentId = event.appointmentId();
        TenantId tenantId = event.tenantId();
        String rawDigits = event.phoneNumber() != null ? event.phoneNumber().strip() : "";
        String number = CrmConversationSupport.digitsForEvolutionApi(rawDigits);
        if (number.isEmpty()) {
            LOG.warn(
                    "Notificação de confirmação omitida: telefone vazio (appointmentId={} tenant={})",
                    appointmentId,
                    tenantId.value());
            return;
        }

        ZoneId zone = calendarZone(event.calendarZoneId());
        LocalDateTime ldt = LocalDateTime.ofInstant(event.startsAt(), zone);
        LocalDate day = ldt.toLocalDate();
        String timeBr = ldt.format(TIME_BR);
        String service = sanitizeServiceNameForCustomer(event.serviceName());

        String text =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        event.appointmentId(),
                        service,
                        nullSafe(event.clientName()),
                        day,
                        timeBr,
                        appointmentConfirmationProperties.getLocationLine(),
                        appointmentConfirmationProperties.getMapsUrl());

        var payload = new WhatsAppTextPayload(tenantId, number, text);

        try {
            var response = whatsAppTextOutboundPort.sendText(payload);
            if (response.isSuccess()) {
                if (!appointmentStore.markAsNotified(event.appointmentId(), response.getMessageId())) {
                    LOG.warn(
                            "Notificação enviada mas não foi possível actualizar tenant_appointments (appointmentId={} tenant={})",
                            appointmentId,
                            tenantId.value());
                }
                String masked = CrmConversationSupport.maskPhoneForAudit(rawDigits);
                LOG.info(
                        "[APP-CONFIRMED] ID: {} | Phone: {} | notificação WhatsApp enviada | messageId={}",
                        appointmentId,
                        masked,
                        response.getMessageId() != null ? response.getMessageId() : "—");
            } else {
                AppointmentAuditLog.appErrorWithAppointmentId(
                        "WhatsApp",
                        event.appointmentId().longValue(),
                        response.getError() != null ? response.getError() : "sendText failure");
            }
        } catch (Exception e) {
            AppointmentAuditLog.appErrorWithAppointmentId(
                    "EvolutionAPI", event.appointmentId().longValue(), e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    @Async
    @EventListener
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        TenantId tenantId = event.tenantId();
        String rawDigits = event.phoneNumber() != null ? event.phoneNumber().strip() : "";
        String number = CrmConversationSupport.digitsForEvolutionApi(rawDigits);
        if (number.isEmpty()) {
            LOG.warn(
                    "Notificação de cancelamento omitida: telefone vazio (appointmentId={} tenant={})",
                    event.appointmentId(),
                    tenantId.value());
            return;
        }

        ZoneId zone = calendarZone(event.calendarZoneId());
        LocalDateTime ldt = LocalDateTime.ofInstant(event.startsAt(), zone);
        String dateBr = ldt.format(DATE_BR);
        String timeBr = ldt.format(TIME_BR);

        String cancelledService = sanitizeServiceNameForCustomer(event.serviceName());
        String text =
                """
                        *Cancelamento confirmado*

                        Olá, *%s*!

                        O agendamento de *%s* foi cancelado.
                        📅 *Data:* %s
                        ⏰ *Horário:* %s

                        Se precisar de um novo horário, é só avisar por aqui.
                        Gestão de atendimento por AxeZap AI
                        """
                        .stripIndent()
                        .formatted(nullSafe(event.clientName()), cancelledService, dateBr, timeBr);

        var payload = new WhatsAppTextPayload(tenantId, number, text);

        try {
            var response = whatsAppTextOutboundPort.sendText(payload);
            if (response.isSuccess()) {
                String masked = CrmConversationSupport.maskPhoneForAudit(rawDigits);
                LOG.info(
                        "[APP-CANCELLED] ID: {} | Phone: {} | notificação WhatsApp enviada (cancel)",
                        event.appointmentId(),
                        masked);
            } else {
                AppointmentAuditLog.appErrorWithAppointmentId(
                        "WhatsApp",
                        event.appointmentId().longValue(),
                        response.getError() != null ? response.getError() : "sendText cancel failure");
            }
        } catch (Exception e) {
            AppointmentAuditLog.appErrorWithAppointmentId(
                    "EvolutionAPI", event.appointmentId().longValue(), e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private static ZoneId calendarZone(String calendarZoneId) {
        if (calendarZoneId == null || calendarZoneId.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(calendarZoneId.strip());
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.strip();
    }

    private static String sanitizeServiceNameForCustomer(String serviceName) {
        String s = nullSafe(serviceName);
        if (s.isBlank()) {
            return "Atendimento";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("criar agendamento")
                || lower.contains("novo agendamento")
                || lower.contains("fazer um agendamento")
                || lower.contains("horário")) {
            return "Atendimento";
        }
        if (s.length() > 48 && (lower.contains("agendamento") || lower.contains("amanhã") || lower.contains("amanha"))) {
            return "Atendimento";
        }
        return s;
    }
}
