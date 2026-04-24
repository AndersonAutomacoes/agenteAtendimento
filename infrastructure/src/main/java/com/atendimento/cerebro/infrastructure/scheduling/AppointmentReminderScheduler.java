package com.atendimento.cerebro.infrastructure.scheduling;

import com.atendimento.cerebro.application.dto.AppointmentReminderCandidate;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.service.AppointmentReminderNotificationService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário: lembretes na véspera do agendamento (dia civil no fuso {@code cerebro.google.calendar.zone}), às 18:00
 * em {@code America/Bahia}.
 */
@Component
public class AppointmentReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentReminderScheduler.class);

    private final TenantAppointmentQueryPort appointmentQuery;
    private final TenantAppointmentStorePort appointmentStore;
    private final AppointmentReminderNotificationService notificationService;
    private final String calendarZoneId;

    public AppointmentReminderScheduler(
            TenantAppointmentQueryPort appointmentQuery,
            TenantAppointmentStorePort appointmentStore,
            AppointmentReminderNotificationService notificationService,
            @Value("${cerebro.google.calendar.zone:America/Bahia}") String calendarZoneId) {
        this.appointmentQuery = appointmentQuery;
        this.appointmentStore = appointmentStore;
        this.notificationService = notificationService;
        this.calendarZoneId =
                calendarZoneId != null && !calendarZoneId.isBlank() ? calendarZoneId.strip() : "America/Bahia";
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "America/Bahia")
    public void sendReminders() {
        ZoneId zone = ZoneId.of(calendarZoneId);
        LocalDate tomorrow = LocalDate.now(zone).plusDays(1);
        List<AppointmentReminderCandidate> appointments =
                appointmentQuery.listAgendadoForReminderOnLocalDate(tomorrow, calendarZoneId);
        LOG.info(
                "Lembrete véspera: {} agendamento(s) para {} (zona={})",
                appointments.size(),
                tomorrow,
                calendarZoneId);

        for (AppointmentReminderCandidate app : appointments) {
            try {
                var response = notificationService.sendReminder(app, zone);
                if (response.isSuccess()) {
                    if (!appointmentStore.markReminderSent(app.id())) {
                        LOG.warn(
                                "Lembrete enviado mas markReminderSent não actualizou (appointmentId={} — estado pode ter mudado)",
                                app.id());
                    } else {
                        LOG.debug("Lembrete véspera enviado (appointmentId={})", app.id());
                    }
                } else {
                    LOG.error(
                            "Falha ao enviar lembrete (appointmentId={} tenant={}): {}",
                            app.id(),
                            app.tenantId().value(),
                            response.getError());
                }
            } catch (Exception e) {
                LOG.error("Erro ao processar lembrete véspera (appointmentId={})", app.id(), e);
            }
        }
    }
}
