package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quando ainda não há lista {@code [slot_options:…]} no histórico e o cliente envia serviço + data + horário (ex.:
 * «Revisão, amanhã às 16:00»), consulta o calendário e devolve confirmação directa ou lista alternativa.
 */
public final class SchedulingExplicitTimeShortcut {

    private static final Pattern HM =
            Pattern.compile("\\b([01]?[0-9]|2[0-3]):([0-5][0-9])\\b");
    private static final DateTimeFormatter PT_DAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    /** Texto entre asteriscos após «disponível para» na confirmação do atalho (mesmo que ruidoso, re-sanitizado). */
    private static final Pattern AVAILABILITY_SERVICE_MARKDOWN =
            Pattern.compile("(?is)dispon[ií]vel\\s+para\\s*\\*([^*]+)\\*");

    private SchedulingExplicitTimeShortcut() {}

    /**
     * @return vazio para deixar o fluxo normal / {@link SchedulingUserReplyNormalizer#expandNumericSlotChoice}
     */
    public static Optional<SlotChoiceExpansion> tryExpand(
            TenantId tenantId,
            String userMessage,
            List<Message> history,
            ZoneId zone,
            AppointmentSchedulingPort scheduling) {
        if (tenantId == null
                || scheduling == null
                || zone == null
                || userMessage == null
                || userMessage.isBlank()) {
            return Optional.empty();
        }
        if (history != null) {
            if (SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(history)) {
                return Optional.empty();
            }
            if (!SchedulingUserReplyNormalizer.parseLastSlotOptionsFromHistory(history).isEmpty()) {
                return Optional.empty();
            }
        }
        Matcher tm = HM.matcher(userMessage.strip());
        if (!tm.find()) {
            return Optional.empty();
        }
        String rawTime = tm.group();
        String timeCanon = SchedulingSlotCapture.normalizeSingleSlotToken(rawTime);
        if (timeCanon == null) {
            return Optional.empty();
        }
        Optional<LocalDate> day = resolveCalendarDay(userMessage.strip(), zone);
        if (day.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = day.get();
        if (d.isBefore(LocalDate.now(zone))) {
            return Optional.empty();
        }
        String serviceHint = extractServiceHint(userMessage.strip());
        if (serviceHint.length() < 2 || !serviceHint.matches(".*\\p{L}.*")) {
            return Optional.empty();
        }
        String iso = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String line = scheduling.checkAvailability(tenantId, iso);
        if (!SchedulingCalendarUserIntent.availabilityLineMatchesRequestedDate(line, d)) {
            return Optional.empty();
        }
        List<String> slots = SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(line);
        List<String> normalizedSlots = SchedulingSlotCapture.normalizeSlotTimes(slots);
        String dayPt = d.format(PT_DAY);

        if (normalizedSlots.isEmpty()) {
            String msg =
                    "Para o dia "
                            + dayPt
                            + " não há horários livres neste momento. Posso verificar outro dia?";
            return Optional.of(
                    new SlotChoiceExpansion(
                            userMessage.strip(),
                            Optional.empty(),
                            Optional.empty(),
                            true,
                            Optional.of(msg),
                            0));
        }
        boolean available = normalizedSlots.contains(timeCanon);
        if (available) {
            SchedulingEnforcedChoice choice = new SchedulingEnforcedChoice(d, timeCanon);
            String hardcoded =
                    "Ótimo! O horário *"
                            + timeCanon
                            + "* do dia *"
                            + dayPt
                            + "* está disponível para *"
                            + serviceHint.strip()
                            + "*. Posso confirmar o agendamento?";
            return Optional.of(
                    new SlotChoiceExpansion(
                            userMessage.strip(),
                            Optional.empty(),
                            Optional.of(choice),
                            true,
                            Optional.of(hardcoded),
                            0));
        }
        String list = SchedulingSlotCapture.buildPremiumFormattedSlotList(d, zone, normalizedSlots);
        String intro =
                "O horário *"
                        + timeCanon
                        + "* não está disponível para *"
                        + dayPt
                        + "*. "
                        + "Seguem outros horários livres:\n\n"
                        + list;
        String hardcoded =
                SchedulingUserReplyNormalizer.appendSchedulingAppendices(intro, normalizedSlots, d);
        return Optional.of(
                new SlotChoiceExpansion(
                        userMessage.strip(),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        Optional.of(hardcoded),
                        0));
    }

    private static Optional<LocalDate> resolveCalendarDay(String userMessage, ZoneId zone) {
        int refYear = LocalDate.now(zone).getYear();
        Optional<LocalDate> br = SchedulingCalendarUserIntent.lastBrazilianDateInText(userMessage, refYear);
        if (br.isPresent()) {
            return br;
        }
        if (SchedulingCalendarUserIntent.asksTomorrow(userMessage)) {
            return Optional.of(SchedulingCalendarUserIntent.tomorrow(zone));
        }
        if (SchedulingCalendarUserIntent.asksToday(userMessage)) {
            return Optional.of(LocalDate.now(zone));
        }
        return Optional.empty();
    }

    private static String extractServiceHint(String message) {
        String s = message;
        Matcher hm = HM.matcher(s);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (hm.find()) {
            sb.append(s, last, hm.start());
            last = hm.end();
        }
        sb.append(s.substring(last));
        s = sb.toString();
        s = s.replaceAll("(?i)\\b(às|as)\\b", " ");
        s = s.replaceAll("(?i)\\b(amanhã|amanha|hoje)\\b", " ");
        s = s.replaceAll("(?i)\\b(depois\\s+de\\s+amanhã|depois\\s+de\\s+amanha)\\b", " ");
        s = s.replaceAll("(?i)\\b(próximo\\s+dia|proximo\\s+dia)\\b", " ");
        s = s.replaceAll("\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?", " ");
        s = s.replaceAll("[,;]", " ");
        s = s.replaceAll("\\s+", " ").strip();
        return sanitizeServiceHintAfterStrip(s);
    }

    /**
     * Remove frases típicas («quero agendar uma … para») para não usar o pedido inteiro como nome do serviço na
     * confirmação.
     */
    public static String sanitizeServiceHintAfterStrip(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.strip();
        s = s.replaceAll("(?i)^\\s*(eu\\s+)?(quero|vou|preciso|gostaria)\\s+(de\\s+)?", " ");
        s = s.replaceAll("(?i)\\b(agendar|marcar|reagendar)\\s+(um|uma|o|a)\\s+", " ");
        s = s.replaceAll("(?i)\\b(agendar|marcar|reagendar)\\b", " ");
        s = s.replaceAll("(?i)^\\s*(um|uma)\\s+", " ");
        s = s.replaceAll("(?i)\\b(para|pra)\\b", " ");
        s = s.replaceAll("(?i)\\s+(pra|para)\\s*$", "");
        s = s.replaceAll("\\s+", " ").strip();
        return s;
    }

    /**
     * Serviço a usar em {@code create_appointment} quando o CRM não tem marcação AGENDADO (ex.: após cancelamento):
     * lê o fragmento «disponível para *…*» da última mensagem do bot com esse formato (atalho de horário explícito).
     */
    public static Optional<String> parseServiceNameForCreateFromHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            String c = m.content();
            if (c == null || c.isBlank()) {
                continue;
            }
            Matcher mat = AVAILABILITY_SERVICE_MARKDOWN.matcher(c);
            if (mat.find()) {
                String cleaned = sanitizeServiceHintAfterStrip(mat.group(1));
                if (!cleaned.isBlank()) {
                    return Optional.of(cleaned);
                }
            }
        }
        return Optional.empty();
    }
}
