package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simula agendamento sem Google: usa {@code google_calendar_id} só como identificador lógico e grava em
 * {@code tenant_appointments}.
 */
@Component
@ConditionalOnProperty(prefix = "cerebro.google.calendar", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockAppointmentSchedulingService implements AppointmentSchedulingPort {

    private static final Logger LOG = LoggerFactory.getLogger(MockAppointmentSchedulingService.class);

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final TenantAppointmentStorePort appointmentStore;
    private final CrmCustomerStorePort crmCustomerStore;
    private final CerebroGoogleCalendarProperties props;

    public MockAppointmentSchedulingService(
            TenantConfigurationStorePort tenantConfigurationStore,
            TenantAppointmentStorePort appointmentStore,
            CrmCustomerStorePort crmCustomerStore,
            CerebroGoogleCalendarProperties props) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.appointmentStore = appointmentStore;
        this.crmCustomerStore = crmCustomerStore;
        this.props = props;
        LOG.warn(
                "MockAppointmentSchedulingService ATIVO (cerebro.google.calendar.mock=true): agendamentos são gravados só na "
                        + "base local — nenhum evento é criado no Google Calendar. Para a API real: "
                        + "CEREBRO_GOOGLE_CALENDAR_MOCK=false e credenciais da service account.");
    }

    @Override
    public String checkAvailability(TenantId tenantId, String isoDate) {
        Optional<String> calId = resolveCalendarId(tenantId);
        if (calId.isEmpty()) {
            return "O calendário ainda não está ligado a este espaço. Peça ao cliente para tentar mais tarde ou contactar o "
                    + "estabelecimento, sem mencionar códigos técnicos.";
        }
        LocalDate day;
        try {
            day = LocalDate.parse(isoDate.strip(), ISO_DATE);
        } catch (DateTimeParseException e) {
            return "A data indicada não pôde ser interpretada. Peça ao cliente uma data clara (dia, mês e ano), sem "
                    + "mencionar formatos técnicos.";
        }
        ZoneId zone = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(zone);
        if (day.isBefore(today)) {
            return "Não é possível consultar disponibilidade para dias que já passaram neste fuso. Peça ao cliente uma data "
                    + "a partir de hoje, com tom cordial.";
        }
        List<WorkingDaySlotPlanner.BusyInterval> busy = List.of();
        var free = WorkingDaySlotPlanner.freeSlotStarts(
                day,
                zone,
                props.getWorkStartHour(),
                props.getWorkEndHour(),
                props.getSlotMinutes(),
                busy);
        return "Calendário (simulado): "
                + calId.get()
                + ". Horários livres em "
                + isoDate
                + " ("
                + zone
                + "): "
                + WorkingDaySlotPlanner.formatSlotsLine(free);
    }

    @Override
    public String createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId) {
        Optional<String> calId = resolveCalendarId(tenantId);
        if (calId.isEmpty()) {
            return "O calendário ainda não está ligado a este espaço. Informe o cliente com cordialidade que não é "
                    + "possível concluir o agendamento agora.";
        }
        LocalDate day;
        try {
            day = LocalDate.parse(isoDate.strip(), ISO_DATE);
        } catch (DateTimeParseException e) {
            return "A data indicada não pôde ser interpretada. Peça ao cliente uma data válida, sem mencionar formatos "
                    + "técnicos.";
        }
        ZoneId zone = ZoneId.of(props.getZone());
        String pastDateError = SchedulingPastDatePolicy.rejectIfDayBeforeToday(day, zone);
        if (pastDateError != null) {
            return pastDateError;
        }
        LocalTime time;
        try {
            time = LocalTime.parse(localTime.strip(), TIME);
        } catch (DateTimeParseException e) {
            return "A hora indicada não pôde ser interpretada. Peça ao cliente um horário claro (ex.: 14:30), sem "
                    + "mencionar códigos técnicos.";
        }
        Instant start = day.atTime(time).atZone(zone).toInstant();
        Instant end = start.plusSeconds(props.getSlotMinutes() * 60L);
        String eventId = "mock-" + UUID.randomUUID();
        String conv = conversationId == null || conversationId.isBlank() ? null : conversationId.strip();
        appointmentStore.insert(
                new TenantAppointmentRecord(tenantId, conv, clientName.strip(), serviceName.strip(), start, end, eventId));
        try {
            crmCustomerStore.recordSuccessfulAppointment(tenantId, conv != null ? conv : "", clientName.strip());
        } catch (RuntimeException e) {
            LOG.warn("CRM após agendamento mock ignorado: {}", e.toString());
        }
        LOG.info(
                "Agendamento mock gravado tenant={} conversationId={} startsAt={} eventId={}",
                tenantId.value(),
                conv,
                start,
                eventId);
        return "Agendamento criado (simulado) no calendário "
                + calId.get()
                + " para "
                + isoDate
                + " "
                + localTime
                + ". ID interno: "
                + eventId;
    }

    /**
     * Usa o ID Google do tenant quando existir; caso contrário um identificador fixo para o MVP mock persistir
     * em {@code tenant_appointments} sem exigir calendário Google configurado no portal.
     */
    private Optional<String> resolveCalendarId(TenantId tenantId) {
        return tenantConfigurationStore
                .findByTenantId(tenantId)
                .map(c -> c.googleCalendarId())
                .map(s -> s == null ? "" : s.strip())
                .filter(s -> !s.isEmpty())
                .or(() -> Optional.of("local"));
    }
}
