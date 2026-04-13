package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Card de confirmação visual para WhatsApp (negrito e separadores compatíveis com o cliente oficial).
 */
public final class AppointmentConfirmationCardFormatter {

    private static final String DIVIDER = "────────";
    private static final DateTimeFormatter PT_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private AppointmentConfirmationCardFormatter() {}

    /**
     * Monta o texto do card após agendamento criado com sucesso.
     *
     * @param locationLine ex.: "Oficina InteliZap - Salvador, BA"
     */
    public static String formatConfirmationCard(
            String serviceName, String clientDisplayName, LocalDate date, String timeHhMm, String locationLine) {
        String service = blankToDash(serviceName);
        String client = blankToDash(clientDisplayName);
        String loc = blankToDash(locationLine);
        String weekday = capitalizeFirst(date.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR));
        String dayStr = date.format(PT_DAY);
        String time = timeHhMm == null || timeHhMm.isBlank() ? "--:--" : timeHhMm.strip();
        return "*✅ AGENDAMENTO CONFIRMADO*"
                + "\n\n"
                + DIVIDER
                + "\n\n"
                + "🛠️ *Serviço:* "
                + service
                + "\n"
                + "👤 *Cliente:* "
                + client
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
                + "\n\n"
                + DIVIDER
                + "\n\n"
                + "_Dica: Chegue 5 minutos antes para garantirmos sua agilidade!_";
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
