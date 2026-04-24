package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.scheduling.AppointmentCalendarValidationResult;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.scheduling.SchedulingPastDatePolicy;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.application.scheduling.ToolCreateAppointmentPreparation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validação de datas/horários e alinhamento da escolha do cliente com {@code [slot_options:…]} antes de gravar no
 * calendário ou expor ao modelo.
 */
public final class AppointmentValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentValidationService.class);

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);

    /**
     * Mapeia o número da opção (1…N) para o HH:mm exibido na lista {@code [slot_options:…]}.
     *
     * @return vazio se o índice for inválido ou a lista vazia
     */
    public Optional<String> mapSlotOptionToTime(int oneBasedIndex, List<String> orderedSlotTimes) {
        if (orderedSlotTimes == null || orderedSlotTimes.isEmpty()) {
            return Optional.empty();
        }
        if (oneBasedIndex < 1 || oneBasedIndex > orderedSlotTimes.size()) {
            return Optional.empty();
        }
        return Optional.of(orderedSlotTimes.get(oneBasedIndex - 1));
    }

    /**
     * Valida data/hora antes de {@code GoogleCalendarService#createEvent} (parse + dia no passado no fuso indicado).
     */
    public AppointmentCalendarValidationResult validateIsoDateAndTimeForCalendar(
            String isoDate, String localTime, ZoneId zone) {
        LocalDate day;
        try {
            day = LocalDate.parse(isoDate.strip(), ISO_DATE);
        } catch (DateTimeParseException e) {
            return AppointmentCalendarValidationResult.invalid(
                    "Não consegui interpretar a data. Pode informar o dia, o mês e o ano?");
        }
        String past = SchedulingPastDatePolicy.rejectIfDayBeforeToday(day, zone);
        if (past != null) {
            return AppointmentCalendarValidationResult.invalid(past);
        }
        LocalTime time;
        try {
            time = LocalTime.parse(localTime.strip(), TIME);
        } catch (DateTimeParseException e) {
            return AppointmentCalendarValidationResult.invalid(
                    "Não consegui interpretar o horário. Pode dizer o horário claramente (por exemplo 14:30)?");
        }
        return AppointmentCalendarValidationResult.ok(day, time);
    }

    /**
     * Camada ferramenta Gemini: escolha forçada pelo backend, âncora de data, e alinhamento opção→HH:mm com o log
     * {@code [slot_options:…]}.
     */
    public ToolCreateAppointmentPreparation prepareGeminiToolCreateAppointment(
            String date,
            String time,
            Optional<SchedulingEnforcedChoice> enforcedChoice,
            Optional<LocalDate> schedulingSlotAnchorDate,
            String latestUserMessage,
            String transcriptBlob) {
        if (enforcedChoice.isPresent()) {
            SchedulingEnforcedChoice r = enforcedChoice.get();
            String iso = r.date().format(ISO_DATE);
            String hhmm = r.timeHhMm();
            return ToolCreateAppointmentPreparation.success(iso, hhmm);
        }
        String dateWorking = date != null ? date.strip() : "";
        String timeWorking = time != null ? time.strip() : "";

        if (schedulingSlotAnchorDate.isPresent()) {
            LocalDate anchor = schedulingSlotAnchorDate.get();
            try {
                LocalDate modelDay = LocalDate.parse(dateWorking, ISO_DATE);
                if (!modelDay.equals(anchor)) {
                    return ToolCreateAppointmentPreparation.failure(
                            "A data não bate com o dia em que a lista de horários foi mostrada. Use o dia "
                                    + anchor.format(ISO_DATE)
                                    + " e o horário que o cliente escolheu.");
                }
            } catch (DateTimeParseException e) {
                return ToolCreateAppointmentPreparation.failure(
                        "Data inválida. Use o mesmo dia da lista ("
                                + anchor.format(ISO_DATE)
                                + ") e o horário que o cliente escolheu.");
            }
        }

        List<String> options = SchedulingUserReplyNormalizer.parseLastSlotOptionsFromTranscript(transcriptBlob);
        Optional<Integer> optIdx = SchedulingUserReplyNormalizer.tryParseOptionIndexFromUserMessage(latestUserMessage);
        if (optIdx.isPresent() && !options.isEmpty()) {
            int n = optIdx.get();
            Optional<String> mapped = mapSlotOptionToTime(n, options);
            if (mapped.isEmpty()) {
                return ToolCreateAppointmentPreparation.failure(
                        "A opção não corresponde à lista (há "
                                + options.size()
                                + " horários). Pode escolher um número de 1 a "
                                + options.size()
                                + " ou digitar o horário como na lista.");
            }
            String expected = mapped.get();
            String normalizedModel = SchedulingSlotCapture.normalizeSingleSlotToken(timeWorking);
            if (normalizedModel == null || !expected.equals(normalizedModel)) {
                LOG.info(
                        "AppointmentValidation: alinhando horário do modelo ({}) ao da opção {} da lista: {}",
                        timeWorking,
                        n,
                        expected);
                timeWorking = expected;
            }
        }

        return ToolCreateAppointmentPreparation.success(dateWorking, timeWorking);
    }

    /**
     * Quando o intervalo pedido já tem evento no Google Calendar ou registo em {@code tenant_appointments} — texto para o
     * Gemini repassar ao utilizador (sem detalhes técnicos).
     */
    public String duplicateSlotConflictMessageForGemini() {
        return "Desculpe, esse horário já está ocupado. Pode escolher outro da lista ou outro dia?";
    }
}
