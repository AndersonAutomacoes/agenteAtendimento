package com.atendimento.cerebro.application.scheduling;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * Extrai a última data dd/MM(yyyy) do texto, usando {@code defaultYear} quando o ano falta.
     */
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
        return Optional.ofNullable(last);
    }

    private static int normalizeYear(String y) {
        int v = Integer.parseInt(y.strip());
        if (y.length() <= 2) {
            return v >= 70 ? 1900 + v : 2000 + v;
        }
        return v;
    }

    /** Verifica se a linha de disponibilidade contém a mesma data ISO pedida (mock e Google usam formatos semelhantes). */
    public static boolean availabilityLineMatchesRequestedDate(String calendarLine, LocalDate requestedDay) {
        if (calendarLine == null || requestedDay == null) {
            return false;
        }
        String iso = requestedDay.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        return calendarLine.contains(iso);
    }
}
