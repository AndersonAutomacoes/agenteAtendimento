package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Card de confirmação visual para WhatsApp (negrito e separadores compatíveis com o cliente oficial).
 */
public final class AppointmentConfirmationCardFormatter {

    private static final String DIVIDER = "────────";
    private static final DateTimeFormatter PT_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Início do card (subconjunto do primeiro parágrafo) formatado por {@link #formatConfirmationCard}. */
    public static final String CONFIRMATION_CARD_HEADLINE = "*Agendamento confirmado*";

    private static final String CONFIRMATION_CARD_DICA_FOOTER =
            "_Se precisar alterar ou cancelar, responda esta mensagem._";

    private AppointmentConfirmationCardFormatter() {}

    /**
     * Remove uma ou mais cópias do card de confirmação (o mesmo formato de {@link #formatConfirmationCard}) do texto
     * do modelo — evita duplicar quando o adaptador volta a anexar o card após {@code create_appointment}.
     */
    public static String stripFormattedConfirmationCards(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String out = text;
        while (out.contains(CONFIRMATION_CARD_HEADLINE)) {
            int start = out.indexOf(CONFIRMATION_CARD_HEADLINE);
            int end = out.indexOf(CONFIRMATION_CARD_DICA_FOOTER, start);
            if (end < 0) {
                break;
            }
            end += CONFIRMATION_CARD_DICA_FOOTER.length();
            String before = out.substring(0, start).replaceAll("\\s+$", "");
            String after = out.substring(end).replaceAll("^\\s+", "");
            if (before.isEmpty()) {
                out = after;
            } else if (after.isEmpty()) {
                out = before;
            } else {
                out = before + "\n\n" + after;
            }
            out = out.strip();
        }
        return out;
    }

    /**
     * Remove o eco do texto devolvido por {@code create_appointment} na resposta do modelo — o card e a notificação
     * WhatsApp já confirmam; evita frases duplicadas.
     */
    public static String stripEchoOfSchedulingCreateToolReturn(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text.strip();
        }
        List<String> keep = new ArrayList<>();
        for (String line : text.split("\n")) {
            String t = line.strip();
            if (t.isEmpty()) {
                keep.add(line);
                continue;
            }
            if (t.startsWith("Agendamento confirmado para") && t.contains("O horário foi registado na agenda")) {
                continue;
            }
            if (t.equals("O horário foi registado na agenda da oficina.")
                    || t.startsWith("O horário foi registado na agenda da oficina.")) {
                continue;
            }
            if (t.startsWith("Agendamento confirmado para")) {
                continue;
            }
            if (t.startsWith("Agendamento criado (simulado)")) {
                continue;
            }
            if (t.startsWith("ID interno:") && t.contains("mock-")) {
                continue;
            }
            keep.add(line);
        }
        return String.join("\n", keep).replaceAll("(?s)\\n{3,}", "\n\n").strip();
    }

    /**
     * Monta o texto do card após agendamento criado com sucesso.
     *
     * @param locationLine ex.: "Oficina InteliZap - Salvador, BA"
     */
    public static String formatConfirmationCard(
            Long appointmentDatabaseId,
            String serviceName,
            String clientDisplayName,
            LocalDate date,
            String timeHhMm,
            String locationLine,
            String mapsUrl) {
        String service = blankToDash(serviceName);
        String client = blankToDash(clientDisplayName);
        String loc = blankToDash(locationLine);
        String weekday = capitalizeFirst(date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR));
        String dayStr = date.format(PT_DAY);
        String time = timeHhMm == null || timeHhMm.isBlank() ? "--:--" : timeHhMm.strip();
        String maps = mapsUrl == null ? "" : mapsUrl.strip();
        String mapsLine = maps.isEmpty() ? "" : "\n📍 *Como chegar:* " + maps;
        String headline =
                appointmentDatabaseId != null
                        ? CONFIRMATION_CARD_HEADLINE + " *#" + appointmentDatabaseId + "*"
                        : CONFIRMATION_CARD_HEADLINE;
        return headline
                + "\n\n"
                + "Olá, *"
                + client
                + "*!"
                + "\n\n"
                + DIVIDER
                + "\n\n"
                + "🛠️ *Serviço:* "
                + service
                + "\n"
                + "📅 *Data:* "
                + weekday
                + ", "
                + dayStr
                + "\n"
                + "🕒 *Horário:* "
                + time
                + "\n"
                + "📍 *Local:* "
                + loc
                + mapsLine
                + "\n\n"
                + DIVIDER
                + "\n\n"
                + CONFIRMATION_CARD_DICA_FOOTER;
    }

    /** Mensagem curta só com o link do mapa (envio separado no WhatsApp). */
    public static String formatMapsFollowUp(String mapsUrl) {
        if (mapsUrl == null || mapsUrl.isBlank()) {
            return "";
        }
        return "📍 *Como chegar:* " + mapsUrl.strip();
    }

    private static String blankToDash(String s) {
        if (s == null || s.isBlank()) {
            return "—";
        }
        return s.strip();
    }

    private static String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
