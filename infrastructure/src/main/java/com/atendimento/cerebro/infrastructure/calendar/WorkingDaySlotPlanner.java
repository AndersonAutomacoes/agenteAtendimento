package com.atendimento.cerebro.infrastructure.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Slots de trabalho no dia, com exclusão de intervalos ocupados (instantes UTC). */
public final class WorkingDaySlotPlanner {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private WorkingDaySlotPlanner() {}

    public record BusyInterval(Instant start, Instant end) {}

    /**
     * Horários de início de slot livres (ordenados), entre workStartHour e workEndHour no {@code zone},
     * excluindo sobreposição com {@code busy}.
     */
    public static List<LocalTime> freeSlotStarts(
            LocalDate day,
            ZoneId zone,
            int workStartHour,
            int workEndHour,
            int slotMinutes,
            List<BusyInterval> busy) {
        ZonedDateTime dayStart = day.atStartOfDay(zone);
        ZonedDateTime workStart = dayStart.withHour(workStartHour).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime workEnd = dayStart.withHour(workEndHour).withMinute(0).withSecond(0).withNano(0);

        List<BusyInterval> sorted = new ArrayList<>(busy);
        sorted.sort(Comparator.comparing(BusyInterval::start));

        List<LocalTime> free = new ArrayList<>();
        ZonedDateTime cursor = workStart;
        while (cursor.plusMinutes(slotMinutes).compareTo(workEnd) <= 0) {
            Instant slotStart = cursor.toInstant();
            Instant slotEnd = cursor.plusMinutes(slotMinutes).toInstant();
            if (!overlapsBusy(slotStart, slotEnd, sorted)) {
                free.add(cursor.toLocalTime().withSecond(0).withNano(0));
            }
            cursor = cursor.plusMinutes(slotMinutes);
        }
        return free;
    }

    private static boolean overlapsBusy(Instant slotStart, Instant slotEnd, List<BusyInterval> sorted) {
        for (BusyInterval b : sorted) {
            if (b.end.isAfter(slotStart) && b.start.isBefore(slotEnd)) {
                return true;
            }
        }
        return false;
    }

    public static String formatSlotsLine(List<LocalTime> slots) {
        if (slots.isEmpty()) {
            return "(nenhum horário livre neste dia com as regras atuais)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(slots.get(i).format(TIME_FMT));
        }
        return sb.toString();
    }
}
