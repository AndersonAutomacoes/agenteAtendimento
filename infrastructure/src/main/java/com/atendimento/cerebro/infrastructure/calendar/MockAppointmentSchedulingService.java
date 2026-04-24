package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.scheduling.AppointmentCalendarValidationResult;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Simula agendamento sem Google: usa {@code google_calendar_id} só como identificador lógico e grava em
 * {@code tenant_appointments}.
 */
@Component
@ConditionalOnProperty(prefix = "cerebro.google.calendar", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockAppointmentSchedulingService implements AppointmentSchedulingPort {

    private static final Logger LOG = LoggerFactory.getLogger(MockAppointmentSchedulingService.class);

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PT_BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final CerebroGoogleCalendarProperties props;
    private final AppointmentValidationService appointmentValidationService;
    private final SchedulingAppointmentTransactionHelper appointmentTx;

    public MockAppointmentSchedulingService(
            TenantConfigurationStorePort tenantConfigurationStore,
            CerebroGoogleCalendarProperties props,
            AppointmentValidationService appointmentValidationService,
            SchedulingAppointmentTransactionHelper appointmentTx) {
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.props = props;
        this.appointmentValidationService = appointmentValidationService;
        this.appointmentTx = appointmentTx;
        LOG.warn(
                "MockAppointmentSchedulingService ATIVO (cerebro.google.calendar.mock=true): agendamentos são gravados só na "
                        + "base local — nenhum evento é criado no Google Calendar. Para a API real: "
                        + "CEREBRO_GOOGLE_CALENDAR_MOCK=false e credenciais da service account.");
    }

    @Override
    public String checkAvailability(TenantId tenantId, String isoDate) {
        Optional<String> calId = resolveCalendarId(tenantId);
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
        ZoneId zone = ZoneId.of(props.getZone());
        LocalDate today = LocalDate.now(zone);
        if (day.isBefore(today)) {
            return "Esse dia já passou. Pode dizer outra data a partir de hoje?";
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
                + day.format(PT_BR_DATE)
                + " ("
                + zone
                + "): "
                + WorkingDaySlotPlanner.formatSlotsLine(free);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateAppointmentResult createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId) {
        Optional<String> calId = resolveCalendarId(tenantId);
        if (calId.isEmpty()) {
            return CreateAppointmentResult.failure(
                    "Desculpe, não consigo concluir o agendamento agora — o calendário ainda não está ligado a este espaço. "
                            + "Pode tentar mais tarde ou falar com a oficina por outro canal.");
        }
        ZoneId zone = ZoneId.of(props.getZone());
        AppointmentCalendarValidationResult validated =
                appointmentValidationService.validateIsoDateAndTimeForCalendar(isoDate, localTime, zone);
        if (!validated.valid()) {
            return CreateAppointmentResult.failure(validated.userMessage());
        }
        LocalDate day = validated.day();
        LocalTime time = validated.time();
        ZonedDateTime startZoned = ZonedDateTime.of(day, time, zone);
        Instant start = startZoned.toInstant();
        Instant end = start.plusSeconds(props.getSlotMinutes() * 60L);
        LocalDate dataGravadaGoogle = start.atZone(zone).toLocalDate();
        LOG.info(
                "confirmação agendamento (mock): DATA_SOLICITADA={} DATA_GRAVADA_GOOGLE={} (fuso {}) startZoned={} eventId=mock tenant={}",
                day,
                dataGravadaGoogle,
                zone.getId(),
                startZoned,
                tenantId.value());
        if (!day.equals(dataGravadaGoogle)) {
            LOG.warn(
                    "DIVERGÊNCIA DATA_SOLICITADA vs DATA_GRAVADA_GOOGLE (mock): solicitada={} gravada={} tenant={}",
                    day,
                    dataGravadaGoogle,
                    tenantId.value());
        }
        String eventId = "mock-" + UUID.randomUUID();
        String conv = conversationId == null || conversationId.isBlank() ? null : conversationId.strip();
        TenantAppointmentRecord record =
                new TenantAppointmentRecord(
                        tenantId, conv, clientName.strip(), serviceName.strip(), start, end, eventId);
        Optional<Long> rowIdOpt = appointmentTx.insertIfNoDbOverlap(tenantId, start, end, record);
        if (rowIdOpt.isEmpty()) {
            LOG.warn(
                    "createAppointment (mock) bloqueado: intervalo já ocupado em tenant_appointments tenant={}",
                    tenantId.value());
            return CreateAppointmentResult.failure(
                    appointmentValidationService.duplicateSlotConflictMessageForGemini());
        }
        long appointmentRowId = rowIdOpt.get();
        LOG.info(
                "Agendamento mock gravado tenant={} conversationId={} startsAt={} eventId={}",
                tenantId.value(),
                conv,
                start,
                eventId);
        return CreateAppointmentResult.success(
                "Agendamento criado (simulado) no calendário "
                        + calId.get()
                        + " para "
                        + day.format(PT_BR_DATE)
                        + " "
                        + localTime
                        + ". ID interno: "
                        + eventId,
                appointmentRowId);
    }

    @Override
    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
        // Calendário simulado: sem chamada à API Google; considera sempre sincronizado.
        return true;
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
