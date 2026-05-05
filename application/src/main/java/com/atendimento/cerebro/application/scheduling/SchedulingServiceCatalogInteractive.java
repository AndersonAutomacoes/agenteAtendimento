package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveKind;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveRow;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Monta {@link WhatsAppInteractiveReply} para listas de serviços a partir de {@code [service_option_map:…]} (mesmo
 * formato de {@link com.atendimento.cerebro.application.service.AppointmentService}).
 */
public final class SchedulingServiceCatalogInteractive {

    private SchedulingServiceCatalogInteractive() {}

    /**
     * Preferência a vagas ({@link SchedulingSlotCapture}); senão catálogo de serviços no mesmo texto do assistente.
     */
    public static Optional<WhatsAppInteractiveReply> mergeWithSlotInteractive(String content, ZoneId calendarZone) {
        Optional<WhatsAppInteractiveReply> slots =
                SchedulingSlotCapture.takeWhatsAppInteractive(content, calendarZone);
        if (slots.isPresent()) {
            return slots;
        }
        return fromAssistantText(content);
    }

    public static Optional<WhatsAppInteractiveReply> fromAssistantText(String content) {
        Optional<String> rawMap = SchedulingUserReplyNormalizer.parseLastServiceOptionMapPayloadFromText(content);
        if (rawMap.isEmpty()) {
            return Optional.empty();
        }
        List<WhatsAppInteractiveRow> rows = rowsFromMapPayload(rawMap.get());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.SERVICES,
                        "Serviços disponíveis",
                        "Toque no botão e escolha uma linha na lista.",
                        List.of(),
                        "",
                        null,
                        "Serviços",
                        "",
                        rows));
    }

    private static List<WhatsAppInteractiveRow> rowsFromMapPayload(String rawMap) {
        List<WhatsAppInteractiveRow> out = new ArrayList<>();
        for (String entry : rawMap.split("\\|")) {
            String e = entry == null ? "" : entry.strip();
            if (e.isEmpty() || !e.contains("=")) {
                continue;
            }
            int eq = e.indexOf('=');
            String k = e.substring(0, eq).strip();
            String v = e.substring(eq + 1).strip();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            try {
                int idx = Integer.parseInt(k);
                if (idx <= 0) {
                    continue;
                }
                out.add(new WhatsAppInteractiveRow("service_" + idx, v, ""));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }
}
