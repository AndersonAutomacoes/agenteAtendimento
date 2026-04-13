package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Calendar com service account global; o calendário do tenant deve estar partilhado com o e-mail da SA.
 * Criação de eventos delegada a {@link GoogleCalendarService} (fuso {@link GoogleCalendarService#CALENDAR_ZONE}).
 */
@Component
@ConditionalOnProperty(prefix = "cerebro.google.calendar", name = "mock", havingValue = "false")
public class GoogleCalendarAppointmentSchedulingService implements AppointmentSchedulingPort {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarAppointmentSchedulingService.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final TenantAppointmentStorePort appointmentStore;
    private final CrmCustomerStorePort crmCustomerStore;
    private final CerebroGoogleCalendarProperties props;
    private final GoogleCalendarService googleCalendarService;

    public GoogleCalendarAppointmentSchedulingService(
            TenantConfigurationStorePort tenantConfigurationStore,
            TenantAppointmentStorePort appointmentStore,
            CrmCustomerStorePort crmCustomerStore,
            CerebroGoogleCalendarProperties props,
            GoogleCalendarService googleCalendarService) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.appointmentStore = appointmentStore;
        this.crmCustomerStore = crmCustomerStore;
        this.props = props;
        this.googleCalendarService = googleCalendarService;
        LOG.info(
                "GoogleCalendarAppointmentSchedulingService ATIVO (cerebro.google.calendar.mock=false): "
                        + "createAppointment e checkAvailability usam a Google Calendar API.");
    }

    @Override
    public String checkAvailability(TenantId tenantId, String isoDate) {
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
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
        ZoneId zone = GoogleCalendarService.CALENDAR_ZONE;
        LocalDate today = LocalDate.now(zone);
        if (day.isBefore(today)) {
            return "Não é possível consultar disponibilidade para dias que já passaram neste fuso. Peça ao cliente uma data "
                    + "a partir de hoje, com tom cordial.";
        }
        try {
            Instant startOfDay = GoogleCalendarService.startOfDayInstant(day);
            Instant endOfDay = GoogleCalendarService.endOfDayExclusiveInstant(day);
            DateTime tMin = new DateTime(startOfDay.toEpochMilli());
            DateTime tMax = new DateTime(endOfDay.toEpochMilli());
            Events events = googleCalendarService.listEvents(calId.get(), tMin, tMax);
            List<WorkingDaySlotPlanner.BusyInterval> busy = new ArrayList<>();
            if (events.getItems() != null) {
                for (Event ev : events.getItems()) {
                    Instant s = eventStart(ev);
                    Instant en = eventEnd(ev);
                    if (s != null && en != null && en.isAfter(s)) {
                        busy.add(new WorkingDaySlotPlanner.BusyInterval(s, en));
                    }
                }
            }
            var free = WorkingDaySlotPlanner.freeSlotStarts(
                    day,
                    zone,
                    props.getWorkStartHour(),
                    props.getWorkEndHour(),
                    props.getSlotMinutes(),
                    busy);
            return "Horários livres em "
                    + isoDate
                    + " ("
                    + zone
                    + "): "
                    + WorkingDaySlotPlanner.formatSlotsLine(free);
        } catch (Exception e) {
            LOG.warn("checkAvailability falhou: {}", e.toString());
            return "Não foi possível consultar a disponibilidade neste momento. Peça ao cliente para tentar de novo dentro de "
                    + "instantes, sem mencionar detalhes técnicos.";
        }
    }

    private static Instant eventStart(Event ev) {
        var edt = ev.getStart();
        if (edt == null) {
            return null;
        }
        if (edt.getDateTime() != null) {
            return Instant.ofEpochMilli(edt.getDateTime().getValue());
        }
        if (edt.getDate() != null) {
            return Instant.ofEpochMilli(edt.getDate().getValue());
        }
        return null;
    }

    private static Instant eventEnd(Event ev) {
        var edt = ev.getEnd();
        if (edt == null) {
            return null;
        }
        if (edt.getDateTime() != null) {
            return Instant.ofEpochMilli(edt.getDateTime().getValue());
        }
        if (edt.getDate() != null) {
            return Instant.ofEpochMilli(edt.getDate().getValue());
        }
        return null;
    }

    @Override
    public String createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId) {
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
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
        ZoneId zone = GoogleCalendarService.CALENDAR_ZONE;
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
        LocalDateTime startLocal = LocalDateTime.of(day, time);
        try {
            String title = serviceName.strip() + " — " + clientName.strip();
            GoogleCalendarCreatedEvent created =
                    googleCalendarService.createEvent(
                            title,
                            startLocal,
                            "Agendamento via agente de atendimento InteliZap.",
                            calId.get(),
                            props.getSlotMinutes());
            String googleId = created.eventId();
            String htmlLink = created.htmlLink() != null ? created.htmlLink() : "";
            Instant start = created.start();
            Instant end = created.end();
            LOG.info(
                    "Agendamento Google Calendar tenant={} eventId={} htmlLink={} startsAt={}",
                    tenantId.value(),
                    googleId,
                    htmlLink,
                    start);
            String conv = conversationId == null || conversationId.isBlank() ? null : conversationId.strip();
            appointmentStore.insert(
                    new TenantAppointmentRecord(
                            tenantId, conv, clientName.strip(), serviceName.strip(), start, end, googleId));
            try {
                crmCustomerStore.recordSuccessfulAppointment(tenantId, conv != null ? conv : "", clientName.strip());
            } catch (RuntimeException e) {
                LOG.warn("CRM após agendamento Google ignorado: {}", e.toString());
            }
            return "Agendamento criado no Google Calendar para "
                    + isoDate
                    + " "
                    + localTime
                    + ". Link: "
                    + htmlLink
                    + " ID: "
                    + googleId;
        } catch (IOException e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            return "Erro ao criar evento no Google Calendar: " + e.getMessage();
        } catch (Exception e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            return "Erro ao criar evento: " + e.getMessage();
        }
    }

    private Optional<String> resolveCalendarId(TenantId tenantId) {
        return tenantConfigurationStore
                .findByTenantId(tenantId)
                .map(c -> c.googleCalendarId())
                .filter(s -> s != null && !s.isBlank())
                .map(String::strip);
    }
}
