package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.service.AppointmentService;
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
    private static final Pattern SPOKEN_HOUR =
            Pattern.compile("(?iu)\\b([01]?\\d|2[0-3])\\s*(?:h|hora|horas)\\b(?:\\s*(?:da|de)\\s*(manh[ãa]|tarde|noite))?");
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
        return tryExpand(tenantId, userMessage, history, zone, scheduling, null);
    }

    /**
     * @param appointmentService se não for nulo, o nome inferido de serviço tem de existir no catálogo; caso contrário
     *     devolve-se a lista de opções em vez de pedir confirmação.
     */
    public static Optional<SlotChoiceExpansion> tryExpand(
            TenantId tenantId,
            String userMessage,
            List<Message> history,
            ZoneId zone,
            AppointmentSchedulingPort scheduling,
            AppointmentService appointmentService) {
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
        Optional<String> timeCanonOpt = parseTimeHhMmFromUserText(userMessage.strip());
        if (timeCanonOpt.isEmpty()) {
            return Optional.empty();
        }
        String timeCanon = timeCanonOpt.get();
        Optional<LocalDate> day = resolveCalendarDay(userMessage.strip(), zone);
        if (day.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = day.get();
        if (d.isBefore(LocalDate.now(zone))) {
            return Optional.empty();
        }
        String serviceHint = extractServiceHint(userMessage.strip());
        if (!isPlausibleExtractedServiceHint(serviceHint)) {
            return Optional.empty();
        }
        if (appointmentService != null) {
            String sh = serviceHint.strip();
            if (!appointmentService.isServiceInTenantCatalog(tenantId, sh)) {
                String msg = appointmentService.buildUnknownServiceReplyWithOptions(tenantId, sh);
                return Optional.of(
                        new SlotChoiceExpansion(
                                userMessage.strip(),
                                Optional.empty(),
                                Optional.empty(),
                                true,
                                Optional.of(msg),
                                0));
            }
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

    /**
     * Interpreta data + hora num texto de utilizador (ex. «amanhã às 16:30») sem exigir nome de serviço. Usado quando o
     * cliente escolhe o serviço na lista mas já tinha indicado dia/hora noutra mensagem.
     */
    public static Optional<SchedulingEnforcedChoice> tryParseExplicitDateAndTimeInUserText(
            String userMessage, ZoneId zone) {
        if (userMessage == null || userMessage.isBlank() || zone == null) {
            return Optional.empty();
        }
        Optional<String> timeCanonOpt = parseTimeHhMmFromUserText(userMessage.strip());
        if (timeCanonOpt.isEmpty()) {
            return Optional.empty();
        }
        String timeCanon = timeCanonOpt.get();
        Optional<LocalDate> day = resolveCalendarDay(userMessage.strip(), zone);
        if (day.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = day.get();
        if (d.isBefore(LocalDate.now(zone))) {
            return Optional.empty();
        }
        return Optional.of(new SchedulingEnforcedChoice(d, timeCanon));
    }

    /**
     * Procura a última mensagem de USER no histórico (ignorando respostas só com número/«opção N») com data+hora
     * concretas, para reutilizar o contexto de um pedido rejeitado no catálogo.
     */
    public static Optional<SchedulingEnforcedChoice> recoverEnforcedChoiceFromUserHistory(
            List<Message> history, ZoneId zone) {
        if (history == null || history.isEmpty() || zone == null) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            String c = m.content();
            if (c == null || c.isBlank()) {
                continue;
            }
            String stripped = c.strip();
            if (SchedulingUserReplyNormalizer.tryParseOptionIndexFromUserMessage(stripped).isPresent()
                    && !HM.matcher(stripped).find()) {
                continue;
            }
            Optional<SchedulingEnforcedChoice> o = tryParseExplicitDateAndTimeInUserText(stripped, zone);
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> resolveCalendarDay(String userMessage, ZoneId zone) {
        int refYear = LocalDate.now(zone).getYear();
        Optional<LocalDate> br = SchedulingCalendarUserIntent.lastBrazilianDateInText(userMessage, refYear);
        if (br.isPresent()) {
            return br;
        }
        Optional<LocalDate> weekday = SchedulingCalendarUserIntent.nextWeekdayMentionedInText(userMessage, zone);
        if (weekday.isPresent()) {
            return weekday;
        }
        if (SchedulingCalendarUserIntent.asksTomorrow(userMessage)) {
            return Optional.of(SchedulingCalendarUserIntent.tomorrow(zone));
        }
        if (SchedulingCalendarUserIntent.asksToday(userMessage)) {
            return Optional.of(LocalDate.now(zone));
        }
        return Optional.empty();
    }

    /** Extrai horário do texto em formato HH:mm (aceita «16:30», «10 horas», «11h da manhã»). */
    public static Optional<String> parseTimeHhMmFromUserText(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        Matcher tm = HM.matcher(userMessage.strip());
        if (tm.find()) {
            String canon = SchedulingSlotCapture.normalizeSingleSlotToken(tm.group());
            return canon == null ? Optional.empty() : Optional.of(canon);
        }
        Matcher spoken = SPOKEN_HOUR.matcher(userMessage.strip());
        if (!spoken.find()) {
            return Optional.empty();
        }
        int hour = Integer.parseInt(spoken.group(1));
        String period = spoken.group(2) != null ? spoken.group(2).toLowerCase(Locale.ROOT) : "";
        if (period.startsWith("manh") && hour == 12) {
            hour = 0;
        } else if ((period.startsWith("tarde") || period.startsWith("noite")) && hour < 12) {
            hour += 12;
        }
        if (hour < 0 || hour > 23) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "%02d:00", hour));
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
        s = s.replaceAll("(?i)\\b(aamanh(ã|a)|amanh(ã|a)|hoje)\\b", " ");
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
        s = s.replaceAll("(?i)\\b(para|pra|par)\\b", " ");
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
        Optional<String> selectedFromFlow = SchedulingUserReplyNormalizer.parseLastSelectedServiceFromHistory(history);
        if (selectedFromFlow.isPresent()) {
            return selectedFromFlow;
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
                if (looksLikeValidServiceName(cleaned)) {
                    return Optional.of(cleaned);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Recupera a pista de serviço textual mais recente em mensagens do cliente (ex.: «Troca de Óleo amanhã às 11:00»),
     * mesmo quando o nome ainda não existe no catálogo.
     */
    public static Optional<String> recoverServiceHintFromUserHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        int lowerBound = Math.max(0, history.size() - 20);
        for (int i = history.size() - 1; i >= lowerBound; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            String c = m.content();
            if (c == null || c.isBlank()) {
                continue;
            }
            String stripped = c.strip();
            if (SchedulingUserReplyNormalizer.tryParseOptionIndexFromUserMessage(stripped).isPresent()
                    && !HM.matcher(stripped).find()) {
                continue;
            }
            String hint = extractServiceHint(stripped);
            if (isPlausibleExtractedServiceHint(hint)) {
                return Optional.of(hint.strip());
            }
        }
        return Optional.empty();
    }

    /**
     * Nome de serviço adequado a {@code [selected_service:…]} no histórico (rejeita eco numérico «1» que não é
     * serviço).
     */
    public static boolean isPlausibleServiceNameForSelectedServiceToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String t = raw.strip();
        if (t.matches("\\d+")) {
            return false;
        }
        return looksLikeValidServiceName(t);
    }

    private static boolean looksLikeValidServiceName(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (raw.strip().length() < 2) {
            return false;
        }
        String lower = raw.strip().toLowerCase(Locale.ROOT);
        if (lower.equals("amanhã")
                || lower.equals("amanha")
                || lower.equals("hoje")
                || lower.equals("depois de amanhã")
                || lower.equals("depois de amanha")) {
            return false;
        }
        if (lower.equals("de")
                || lower.equals("da")
                || lower.equals("do")
                || lower.equals("um")
                || lower.equals("uma")
                || lower.equals("o")
                || lower.equals("a")) {
            return false;
        }
        return raw.matches(".*\\p{L}.*");
    }

    /**
     * Indica se o pedido de atalho contém indício real de serviço (não só intenção de agendar + data/hora).
     */
    public static boolean isPlausibleServiceHintForShortcut(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        return isPlausibleExtractedServiceHint(extractServiceHint(userMessage.strip()));
    }

    private static boolean isPlausibleExtractedServiceHint(String hint) {
        if (hint == null) {
            return false;
        }
        if (hint.length() < 3) {
            return false;
        }
        if (!hint.matches(".*\\p{L}.*")) {
            return false;
        }
        return looksLikeValidServiceName(hint);
    }
}
