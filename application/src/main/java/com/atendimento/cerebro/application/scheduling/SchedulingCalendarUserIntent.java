package com.atendimento.cerebro.application.scheduling;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infere datas relativas a partir do texto do utilizador (ex.: «amanhã») para validar chamadas a
 * {@code check_availability} no backend.
 */
public final class SchedulingCalendarUserIntent {

    private static final Pattern AMANHA =
            Pattern.compile("(?iu)(amanhã|amanha|próximo\\s+dia|proximo\\s+dia)");
    private static final Pattern HOJE = Pattern.compile("(?iu)\\b(hoje|agora\\s+mismo)\\b");
    /** dd/MM ou dd/MM/yyyy */
    private static final Pattern BR_DATE =
            Pattern.compile("(?<!\\d)(\\d{1,2})\\s*/\\s*(\\d{1,2})(?:\\s*/\\s*(\\d{2,4}))?(?!\\d)");
    private static final Pattern PT_WEEKDAY =
            Pattern.compile(
                    "(?iu)\\b(seg(?:unda)?(?:-feira)?|ter(?:ca|ça)?(?:-feira)?|qua(?:rta)?(?:-feira)?|qui(?:nta)?(?:-feira)?|sex(?:ta)?(?:-feira)?|s[áa]b(?:ado)?|dom(?:ingo)?)\\b");

    private SchedulingCalendarUserIntent() {}

    /** «Amanhã» no fuso do calendário. */
    public static LocalDate tomorrow(ZoneId zone) {
        return LocalDate.now(zone).plusDays(1);
    }

    public static boolean asksTomorrow(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String n = Normalizer.normalize(userMessage.strip(), Normalizer.Form.NFKC);
        return AMANHA.matcher(n).find();
    }

    public static boolean asksToday(String userMessage) {
        return userMessage != null && HOJE.matcher(userMessage.strip()).find();
    }

    /**
     * Se o texto mencionar «amanhã», devolve o {@link LocalDate} esperado; caso contrário vazio.
     */
    public static Optional<LocalDate> expectedDayIfTomorrowMentioned(String userMessage, ZoneId zone) {
        if (userMessage == null || userMessage.isBlank() || !asksTomorrow(userMessage)) {
            return Optional.empty();
        }
        return Optional.of(tomorrow(zone));
    }

    /**
     * Extrai a última data dd/MM(yyyy) do texto, ou em formato falado «27 do 4 de 2026», usando {@code
     * defaultYear} para datas só com dia/mês.
     */
    private static final Pattern COLOQUIAL_DAY_OF_MONTH_IN_YEAR =
            Pattern.compile(
                    "(?i)(?<!\\d)(\\d{1,2})\\s+do?\\s+(\\d{1,2})\\s+de\\s+([0-9]{2,4})\\b(?!\\s*/\\s*\\d)",
                    Pattern.CASE_INSENSITIVE);

    public static Optional<LocalDate> lastBrazilianDateInText(String text, int defaultYear) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher m = BR_DATE.matcher(text);
        LocalDate last = null;
        while (m.find()) {
            int d = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            String yG = m.group(3);
            int year = yG == null ? defaultYear : normalizeYear(yG);
            try {
                last = LocalDate.of(year, month, d);
            } catch (Exception ignored) {
                // skip invalid
            }
        }
        if (last != null) {
            return Optional.of(last);
        }
        Matcher c = COLOQUIAL_DAY_OF_MONTH_IN_YEAR.matcher(text);
        while (c.find()) {
            int d = Integer.parseInt(c.group(1));
            int month = Integer.parseInt(c.group(2));
            int year = normalizeYear(c.group(3).strip());
            try {
                last = LocalDate.of(year, month, d);
            } catch (Exception ignored) {
                // skip invalid
            }
        }
        if (last == null) {
            last = lastCommaSeparatedDayMonthYearInText(text);
        }
        return Optional.ofNullable(last);
    }

    /**
     * STT: «70, 27, 4, 2026, 12 horas» (código + dia + mês + ano) ou «27, 4, 2026».
     */
    private static LocalDate lastCommaSeparatedDayMonthYearInText(String text) {
        LocalDate best = null;
        Matcher m4 =
                Pattern.compile(
                                "(?i)(?:^|[\\s,.;])(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*([0-9]{4})")
                        .matcher(text);
        while (m4.find()) {
            int maybeId = Integer.parseInt(m4.group(1));
            int day = Integer.parseInt(m4.group(2));
            int month = Integer.parseInt(m4.group(3));
            int year = Integer.parseInt(m4.group(4));
            if (year < 2000 || year > 2100) {
                continue;
            }
            if (day >= 1
                    && day <= 31
                    && month >= 1
                    && month <= 12
                    && maybeId >= 1
                    && maybeId <= 99) {
                try {
                    best = LocalDate.of(year, month, day);
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        if (best != null) {
            return best;
        }
        Matcher m3 =
                Pattern.compile("(?i)(?<!\\d)(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*([0-9]{4})\\b")
                        .matcher(text);
        while (m3.find()) {
            int day = Integer.parseInt(m3.group(1));
            int month = Integer.parseInt(m3.group(2));
            int year = Integer.parseInt(m3.group(3));
            if (year < 2000 || year > 2100) {
                continue;
            }
            if (day >= 1 && day <= 31 && month >= 1 && month <= 12) {
                try {
                    best = LocalDate.of(year, month, day);
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        return best;
    }

    /** Próxima ocorrência (incluindo hoje) de dia da semana em pt-BR (ex.: «quinta-feira», «terça», «sabado»). */
    public static Optional<LocalDate> nextWeekdayMentionedInText(String text, ZoneId zone) {
        if (text == null || text.isBlank() || zone == null) {
            return Optional.empty();
        }
        Matcher m = PT_WEEKDAY.matcher(text);
        DayOfWeek target = null;
        while (m.find()) {
            target = mapPortugueseWeekday(m.group(1));
        }
        if (target == null) {
            return Optional.empty();
        }
        LocalDate now = LocalDate.now(zone);
        int delta = (target.getValue() - now.getDayOfWeek().getValue() + 7) % 7;
        return Optional.of(now.plusDays(delta));
    }

    private static DayOfWeek mapPortugueseWeekday(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = Normalizer.normalize(raw.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        n = n.replace("-feira", "");
        if (n.startsWith("seg")) {
            return DayOfWeek.MONDAY;
        }
        if (n.startsWith("ter")) {
            return DayOfWeek.TUESDAY;
        }
        if (n.startsWith("qua")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (n.startsWith("qui")) {
            return DayOfWeek.THURSDAY;
        }
        if (n.startsWith("sex")) {
            return DayOfWeek.FRIDAY;
        }
        if (n.startsWith("sab") || n.startsWith("sáb")) {
            return DayOfWeek.SATURDAY;
        }
        if (n.startsWith("dom")) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }

    private static int normalizeYear(String y) {
        int v = Integer.parseInt(y.strip());
        if (y.length() <= 2) {
            return v >= 70 ? 1900 + v : 2000 + v;
        }
        return v;
    }

    private static final DateTimeFormatter PT_BR_FULL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    /**
     * Verifica se a linha de disponibilidade contém a data pedida (ISO yyyy-MM-dd ou apresentação pt-BR dd/MM/yyyy).
     */
    public static boolean availabilityLineMatchesRequestedDate(String calendarLine, LocalDate requestedDay) {
        if (calendarLine == null || requestedDay == null) {
            return false;
        }
        String iso = requestedDay.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String ptBr = requestedDay.format(PT_BR_FULL);
        return calendarLine.contains(iso) || calendarLine.contains(ptBr);
    }
}
