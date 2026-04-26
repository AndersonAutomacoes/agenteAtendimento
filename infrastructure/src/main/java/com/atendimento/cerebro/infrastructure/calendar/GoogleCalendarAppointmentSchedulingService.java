package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.scheduling.AppointmentCalendarValidationResult;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.logging.AppointmentAuditLog;
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
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final CerebroGoogleCalendarProperties props;
    private final GoogleCalendarService googleCalendarService;
    private final AppointmentValidationService appointmentValidationService;
    private final SchedulingAppointmentTransactionHelper appointmentTx;

    public GoogleCalendarAppointmentSchedulingService(
            TenantConfigurationStorePort tenantConfigurationStore,
            CerebroGoogleCalendarProperties props,
            GoogleCalendarService googleCalendarService,
            AppointmentValidationService appointmentValidationService,
            SchedulingAppointmentTransactionHelper appointmentTx) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.props = props;
        this.googleCalendarService = googleCalendarService;
        this.appointmentValidationService = appointmentValidationService;
        this.appointmentTx = appointmentTx;
        LOG.info(
                "GoogleCalendarAppointmentSchedulingService ATIVO (cerebro.google.calendar.mock=false): "
                        + "createAppointment e checkAvailability usam a Google Calendar API.");
    }

    @Override
    public String checkAvailability(TenantId tenantId, String isoDate) {
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
        if (calId.isEmpty()) {
            return "O calendário ainda não está ligado a este espaço. Pode voltar a tentar daqui a pouco ou falar com a "
                    + "oficina por outro canal.";
        }
        LocalDate day;
        try {
            day = LocalDate.parse(isoDate.strip(), ISO_DATE);
        } catch (DateTimeParseException e) {
            return "Não consegui entender a data. Pode dizer o dia, o mês e o ano (ou escolher na lista)?";
        }
        ZoneId zone = GoogleCalendarService.CALENDAR_ZONE;
        LocalDate today = LocalDate.now(zone);
        if (day.isBefore(today)) {
            return "Esse dia já passou. Pode dizer outra data a partir de hoje?";
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
            return "Não foi possível consultar a disponibilidade agora. Tente de novo daqui a pouco.";
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
            Long serviceId,
            String serviceName,
            String conversationId) {
        Optional<String> calId = googleCalendarService.resolveEffectiveCalendarId(resolveCalendarId(tenantId));
        if (calId.isEmpty()) {
            return CreateAppointmentResult.failure(
                    "Desculpe, não consigo concluir o agendamento agora — o calendário ainda não está ligado a este espaço. "
                            + "Pode tentar mais tarde ou falar com a oficina por outro canal.");
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
            AppointmentAuditLog.appError("GoogleAPI", e.getMessage() != null ? e.getMessage() : e.toString());
            return CreateAppointmentResult.failure(
                    "Não foi possível confirmar se este horário ainda está livre. Pode sugerir outro horário ou tentar de novo "
                            + "daqui a instantes.");
        }
        try {
            String title = formatCalendarEventSummary(serviceName, time, clientName);
            String description = formatCalendarEventDescription(conversationId);
            GoogleCalendarCreatedEvent created =
                    googleCalendarService.createEvent(
                            title,
                            startLocal,
                            description,
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
            TenantAppointmentRecord record =
                    new TenantAppointmentRecord(
                            tenantId, conv, clientName.strip(), serviceId, serviceName.strip(), start, end, googleId);
            Optional<Long> rowIdOpt = appointmentTx.insertIfNoDbOverlap(tenantId, start, end, record);
            if (rowIdOpt.isEmpty()) {
                try {
                    googleCalendarService.deleteEvent(calId.get(), googleId);
                } catch (Exception de) {
                    LOG.error(
                            "Compensação: falha ao apagar evento Google após conflito na base tenant={} eventId={}",
                            tenantId.value(),
                            googleId,
                            de);
                }
                AppointmentAuditLog.appError(
                        "GoogleAPI", "Conflito na base após criar evento no Google — evento compensado no calendário");
                return CreateAppointmentResult.failure(
                        appointmentValidationService.duplicateSlotConflictMessageForGemini());
            }
            return CreateAppointmentResult.success(
                    "Agendamento confirmado para "
                            + day.format(PT_BR_DATE)
                            + " às "
                            + localTime
                            + ". O horário foi registado na agenda da oficina.",
                    rowIdOpt.get());
        } catch (IOException e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            AppointmentAuditLog.appError("GoogleAPI", e.getMessage() != null ? e.getMessage() : e.toString());
            return CreateAppointmentResult.failure("Erro ao criar evento no Google Calendar: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("createAppointment falhou: {}", e.toString());
            AppointmentAuditLog.appError("GoogleAPI", e.getMessage() != null ? e.getMessage() : e.toString());
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

    static String formatCalendarEventSummary(String serviceName, LocalTime time, String clientName) {
        String service = normalizeSummaryPart(serviceName, "SERVICO");
        String client = normalizeSummaryPart(clientName, "CLIENTE");
        String hhmm = time == null ? "--:--" : HH_MM.format(time);
        return service + " - " + hhmm + " - " + client;
    }

    static String formatCalendarEventDescription(String conversationId) {
        StringBuilder out = new StringBuilder("Agendamento via agente de atendimento AxeZap.");
        String waDigits = extractWhatsAppDigitsFromConversationId(conversationId);
        if (waDigits != null) {
            out.append("\nWhatsApp cliente: https://wa.me/").append(waDigits);
        }
        return out.toString();
    }

    private static String extractWhatsAppDigitsFromConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String raw = conversationId.strip();
        if (!raw.startsWith("wa-")) {
            return null;
        }
        String digits = raw.substring(3).replaceAll("\\D+", "");
        return digits.isBlank() ? null : digits;
    }

    private static String normalizeSummaryPart(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.strip().replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("pt-BR"));
    }
}
