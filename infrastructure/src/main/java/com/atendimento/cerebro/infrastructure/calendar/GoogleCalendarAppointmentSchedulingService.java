package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.scheduling.AppointmentCalendarValidationResult;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
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
    private static final DateTimeFormatter PT_BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final TenantAppointmentStorePort appointmentStore;
    private final CrmCustomerStorePort crmCustomerStore;
    private final CerebroGoogleCalendarProperties props;
    private final GoogleCalendarService googleCalendarService;
    private final AppointmentValidationService appointmentValidationService;

    public GoogleCalendarAppointmentSchedulingService(
            TenantConfigurationStorePort tenantConfigurationStore,
            TenantAppointmentStorePort appointmentStore,
            CrmCustomerStorePort crmCustomerStore,
            CerebroGoogleCalendarProperties props,
            GoogleCalendarService googleCalendarService,
            AppointmentValidationService appointmentValidationService) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.appointmentStore = appointmentStore;
        this.crmCustomerStore = crmCustomerStore;
        this.props = props;
        this.googleCalendarService = googleCalendarService;
        this.appointmentValidationService = appointmentValidationService;
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
                    + day.format(PT_BR_DATE)
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
    public CreateAppointmentResult createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId) {
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
        if (calId.isEmpty()) {
            return CreateAppointmentResult.failure(
                    "O calendário ainda não está ligado a este espaço. Informe o cliente com cordialidade que não é "
                            + "possível concluir o agendamento agora.");
        }
        ZoneId zone = GoogleCalendarService.CALENDAR_ZONE;
        AppointmentCalendarValidationResult validated =
                appointmentValidationService.validateIsoDateAndTimeForCalendar(isoDate, localTime, zone);
        if (!validated.valid()) {
            return CreateAppointmentResult.failure(validated.userMessage());
        }
        LocalDate day = validated.day();
        LocalTime time = validated.time();
        ZonedDateTime startZoned = ZonedDateTime.of(day, time, zone);
        LocalDateTime startLocal = startZoned.toLocalDateTime();
        Instant slotStart = startZoned.toInstant();
        Instant slotEnd = slotStart.plusSeconds((long) props.getSlotMinutes() * 60L);
        try {
            if (!googleCalendarService.findEventsOverlapping(slotStart, slotEnd, calId.get()).isEmpty()) {
                LOG.warn(
                        "createAppointment bloqueado: intervalo já ocupado no Google Calendar tenant={} calendarId={}",
                        tenantId.value(),
                        calId.get());
                return CreateAppointmentResult.failure(
                        appointmentValidationService.duplicateSlotConflictMessageForGemini());
            }
        } catch (Exception e) {
            LOG.warn("Verificação de duplicidade no Google Calendar falhou: {}", e.toString());
            return CreateAppointmentResult.failure(
                    "Não foi possível confirmar se este horário continua livre. Peça ao cliente para escolher outro horário ou "
                            + "tentar novamente dentro de instantes, sem mencionar detalhes técnicos.");
        }
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
            LocalDate dataGravadaGoogle = start.atZone(zone).toLocalDate();
            LOG.info(
                    "confirmação agendamento: DATA_SOLICITADA={} DATA_GRAVADA_GOOGLE={} (fuso {}) startZoned={} eventId={} tenant={}",
                    day,
                    dataGravadaGoogle,
                    zone.getId(),
                    startZoned,
                    googleId,
                    tenantId.value());
            if (!day.equals(dataGravadaGoogle)) {
                LOG.warn(
                        "DIVERGÊNCIA DATA_SOLICITADA vs DATA_GRAVADA_GOOGLE: solicitada={} gravada={} eventId={} tenant={}",
                        day,
                        dataGravadaGoogle,
                        googleId,
                        tenantId.value());
            }
            LOG.info(
                    "Agendamento Google Calendar tenant={} eventId={} htmlLink={} startsAt={}",
                    tenantId.value(),
                    googleId,
                    htmlLink,
                    start);
            // htmlLink e eventId ficam só em log — o cliente final não deve receber URL do Google Calendar.
            String conv = conversationId == null || conversationId.isBlank() ? null : conversationId.strip();
            long appointmentRowId =
                    appointmentStore.insert(
                            new TenantAppointmentRecord(
                                    tenantId, conv, clientName.strip(), serviceName.strip(), start, end, googleId));
            try {
                crmCustomerStore.recordSuccessfulAppointment(tenantId, conv != null ? conv : "", clientName.strip());
            } catch (RuntimeException e) {
                LOG.warn("CRM após agendamento Google ignorado: {}", e.toString());
            }
            return CreateAppointmentResult.success(
                    "Agendamento confirmado para "
                            + day.format(PT_BR_DATE)
                            + " às "
                            + localTime
                            + ". O horário foi registado na agenda da oficina.",
                    appointmentRowId);
        } catch (IOException e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            return CreateAppointmentResult.failure("Erro ao criar evento no Google Calendar: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            return CreateAppointmentResult.failure("Erro ao criar evento: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
        if (googleEventId == null || googleEventId.isBlank()) {
            return false;
        }
        String gid = googleEventId.strip();
        if (gid.startsWith("mock-")) {
            LOG.debug("deleteCalendarEvent: evento mock, sem API Google tenant={}", tenantId.value());
            return true;
        }
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
        if (calId.isEmpty()) {
            LOG.warn(
                    "deleteCalendarEvent: calendário não configurado tenant={} — evento Google não apagado (googleEventId={})",
                    tenantId.value(),
                    gid);
            return false;
        }
        try {
            googleCalendarService.deleteEvent(calId.get(), gid);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
