package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Substitui placeholders {@code {{current_date}}} e {@code {{tomorrow_date}}} na persona do tenant pelo calendário no
 * fuso configurado (ex.: Salvador).
 */
public final class SystemPromptPlaceholders {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PT_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private SystemPromptPlaceholders() {}

    /**
     * Formato de cada placeholder: {@code dd/MM/yyyy (yyyy-MM-DD)}.
     */
    public static String apply(String systemPrompt, ZoneId zone) {
        if (systemPrompt == null || !systemPrompt.contains("{{")) {
            return systemPrompt != null ? systemPrompt : "";
        }
        ZoneId z = zone != null ? zone : ZoneId.of("America/Bahia");
        LocalDate today = LocalDate.now(z);
        LocalDate tomorrow = today.plusDays(1);
        return systemPrompt
                .replace("{{current_date}}", PT_BR.format(today) + " (" + ISO.format(today) + ")")
                .replace("{{tomorrow_date}}", PT_BR.format(tomorrow) + " (" + ISO.format(tomorrow) + ")");
    }
}
