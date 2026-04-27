package com.atendimento.cerebro.application.scheduling;

import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai o ID de agendamento em pedidos de reagendamento: com verbo (ex. «reagendar o 66») ou, após a lista
 * do assistente, o formato curto «66, 27/04/2026 12:00» / «66, 27 do 4 de 2026, 12 horas».
 */
public final class SchedulingRescheduleIdExtractor {

    /**
     * Alinhado a {@code ChatService}: reagendar/remarcar/mudar + (opcional) código/ID + número.
     */
    private static final Pattern STRICT_VERB_THEN_ID =
            Pattern.compile(
                    "(?i)\\b(reagend\\w*|remarc\\w*|mud\\w*)\\b(?:\\s+o)?(?:\\s+(?:agendamento|atendimento))?(?:\\s+"
                            + "(?:id|c[oó]digo|n[uú]mero|numero|n°|#))?\\s*[:#-]?\\s*(\\d{1,19})\\b");

    /** Código sozinho no início seguido de vírgula/ponto e vírgula, quando há também data+hora. */
    private static final Pattern LEADING_ID_AND_COMMA = Pattern.compile("^(\\d{1,19})\\s*[,;]\\s*.+$");

    private SchedulingRescheduleIdExtractor() {}

    /**
     * STT: «70» + «27 do 4» muitas vezes viram «7027 do 4». Separa o número depois de «atendimento/agendamento» ou
     * quando o padrão é claramente id+dia+«do 4 de …».
     */
    public static String normalizeVoiceSttRescheduleText(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return userMessage;
        }
        String s = userMessage.strip();
        s =
                s.replaceAll(
                        "(?i)(?<=(?:atendimento|agendamento)\\s)(\\d{2})(0[1-9]|[12]\\d|3[01])(?=\\s+do\\s+)",
                        "$1, $2");
        s =
                s.replaceAll(
                        "(?i)(?<![,\\d])(\\d{2})(0[1-9]|[12]\\d|3[01])\\s+do\\s+4\\s+de\\s+(20\\d{2})",
                        "$1, $2 do 4 de $3");
        return s;
    }

    /**
     * @return o ID de agendamento se o texto combina com verbo+ID, ou «N, …» e o corpo reúne data e hora
     *     interpretáveis.
     */
    public static Optional<Long> extractFromUserText(String userMessage, ZoneId zone) {
        if (userMessage == null || userMessage.isBlank() || zone == null) {
            return Optional.empty();
        }
        String s = normalizeVoiceSttRescheduleText(userMessage.strip());
        Optional<Long> strict = idFromPattern(STRICT_VERB_THEN_ID, s, 2);
        if (strict.isPresent()) {
            return strict;
        }
        if (!LEADING_ID_AND_COMMA.matcher(s).matches()) {
            return Optional.empty();
        }
        if (SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(s, zone).isEmpty()) {
            return Optional.empty();
        }
        Matcher m = LEADING_ID_AND_COMMA.matcher(s);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Long> idFromPattern(Pattern p, String s, int idGroup) {
        Matcher m = p.matcher(s);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(m.group(idGroup)));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }
}
