package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normaliza respostas do cliente no fluxo de agendamento: mapeia número da opção (ex.: {@code 1}, {@code opção 2})
 * para HH:mm com base na lista persistida no histórico ({@code [slot_options:...]}).
 */
public final class SchedulingUserReplyNormalizer {

    /** Sufixo interno gravado na última mensagem do assistente (não mostrar ao utilizador final). */
    public static final String SLOT_OPTIONS_APPENDIX_TOKEN = "[slot_options:";

    /** Data yyyy-MM-DD da última {@code check_availability} (obrigatória em {@code create_appointment}). */
    public static final String SLOT_DATE_APPENDIX_TOKEN = "[slot_date:";

    /** Rascunho data|hora após escolha por número; confirmado na mensagem seguinte. */
    public static final String SCHEDULING_DRAFT_APPENDIX_TOKEN = "[scheduling_draft:";

    private static final Pattern APPENDIX_ANY = Pattern.compile("\\[slot_options:([^\\]]+)\\]");
    private static final Pattern DRAFT_ANY = Pattern.compile("\\[scheduling_draft:([^\\|]+)\\|([^\\]]+)\\]");

    /** Extrai HH:mm na ordem (não usar split por vírgula: evita desalinhamento se o payload tiver anomalias). */
    private static final Pattern SLOT_OPTION_TIMES_IN_ORDER = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
    private static final Pattern SLOT_DATE_ANY = Pattern.compile("\\[slot_date:([^\\]]+)\\]");
    private static final Pattern TIME = Pattern.compile("\\b([01]?[0-9]|2[0-3]):([0-5][0-9])\\b");
    /** Índice da opção (1…999) quando a mensagem é só dígitos. */
    private static final Pattern SOLO_DIGITS = Pattern.compile("^\\s*(\\d{1,3})\\s*$");
    /** "opção 2", "opcao 2", "op 3" */
    private static final Pattern OPCAO =
            Pattern.compile("(?i)\\bop(?:ção|cao|çao)?\\s*[#:.\\-]?\\s*(\\d{1,3})\\b");

    private static final Pattern SHORT_CONFIRM =
            Pattern.compile(
                    "^(sim|sí|confirmo|confirmado|pode|ok|isso|perfeito|fechado|pode\\s+ser|pode\\s+confirmar)\\b[.!\\s]*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final DateTimeFormatter PT_BR_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private SchedulingUserReplyNormalizer() {}

    public static String appendSlotOptionsAppendix(String content, List<String> orderedSlotTimes) {
        if (content == null || orderedSlotTimes == null || orderedSlotTimes.isEmpty()) {
            return content;
        }
        String base = content.strip();
        if (base.contains(SLOT_OPTIONS_APPENDIX_TOKEN)) {
            return base;
        }
        return base + "\n\n" + SLOT_OPTIONS_APPENDIX_TOKEN + String.join(",", orderedSlotTimes) + "]";
    }

    /** Anexa horários + data ISO da consulta ao histórico (para o modelo não confundir «hoje» com o dia da lista). */
    public static String appendSchedulingAppendices(
            String content, List<String> orderedSlotTimes, LocalDate requestedDate) {
        String withSlots = appendSlotOptionsAppendix(content, orderedSlotTimes);
        if (withSlots == null || requestedDate == null) {
            return withSlots;
        }
        String base = withSlots.strip();
        if (base.contains(SLOT_DATE_APPENDIX_TOKEN)) {
            return base;
        }
        String iso = requestedDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return base + "\n" + SLOT_DATE_APPENDIX_TOKEN + iso + "]";
    }

    /** Remove o apêndice interno antes de expor o texto a operadores ou APIs de histórico. */
    public static String stripInternalSlotAppendix(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String s = content;
        s = s.replaceAll("(?s)\\n*\\n\\[slot_options:[^\\]]+\\]", "");
        s = s.replaceAll("(?s)\\n*\\[slot_date:[^\\]]+\\]", "");
        s = s.replaceAll("(?s)\\n*\\n\\[scheduling_draft:[^\\]]+\\]", "");
        return s.strip();
    }

    public static String appendSchedulingDraft(String content, SchedulingEnforcedChoice draft) {
        if (content == null || draft == null) {
            return content;
        }
        String base = content.strip();
        if (base.contains(SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
            return base;
        }
        String iso = draft.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return base + "\n\n" + SCHEDULING_DRAFT_APPENDIX_TOKEN + iso + "|" + draft.timeHhMm() + "]";
    }

    /**
     * Se a mensagem for só número/opção e existir lista no histórico, substitui por confirmação explícita em HH:mm
     * (facilita {@code create_appointment}) e devolve {@link SchedulingEnforcedChoice} para o port de IA aplicar
     * sem depender de ThreadLocal. Escolha por índice (1–20) fica em rascunho até o cliente confirmar com sim/ok.
     */
    public static SlotChoiceExpansion expandNumericSlotChoice(String userMessage, List<Message> history) {
        if (userMessage == null || history == null) {
            return SlotChoiceExpansion.unchanged(userMessage);
        }
        String normalized =
                Normalizer.normalize(userMessage == null ? "" : userMessage.strip(), Normalizer.Form.NFKC);

        Optional<SchedulingEnforcedChoice> draftInHist = parseLastDraftFromHistory(history);
        if (draftInHist.isPresent() && looksLikeConfirmation(normalized)) {
            SchedulingEnforcedChoice d = draftInHist.get();
            String iso = d.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
            return new SlotChoiceExpansion(
                    "O cliente confirmou o agendamento. Chame create_appointment com date="
                            + iso
                            + " e time="
                            + d.timeHhMm()
                            + ".",
                    Optional.of(d),
                    Optional.empty(),
                    false,
                    Optional.empty(),
                    0);
        }

        List<String> options = parseLastSlotOptionsFromHistory(history);
        if (options.isEmpty()) {
            return SlotChoiceExpansion.unchanged(userMessage);
        }
        Optional<LocalDate> agreedDate = parseLastSlotDateFromHistory(history);
        boolean byIndex = isSelectionByOptionIndex(normalized, options);
        int optNum = parseOptionNumber(normalized);
        Optional<String> resolved = resolveChoiceToTime(normalized, options);
        return resolved
                .map(t -> buildSlotExpansion(agreedDate, t, byIndex, optNum))
                .orElseGet(() -> SlotChoiceExpansion.unchanged(userMessage));
    }

    static boolean looksLikeConfirmation(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String t = normalized.strip();
        return t.length() <= 96 && SHORT_CONFIRM.matcher(t).matches();
    }

    /** Verdadeiro se a escolha foi por «opção N» ou só dígitos (índice), não por HH:mm explícito. */
    static boolean isSelectionByOptionIndex(String normalized, List<String> options) {
        if (normalized == null || options.isEmpty()) {
            return false;
        }
        Matcher tm = TIME.matcher(normalized);
        if (tm.find()) {
            return false;
        }
        Matcher op = OPCAO.matcher(normalized);
        if (op.find()) {
            return true;
        }
        Matcher solo = SOLO_DIGITS.matcher(normalized);
        return solo.matches();
    }

    private static SlotChoiceExpansion buildSlotExpansion(
            Optional<LocalDate> agreedDate, String time, boolean selectionByIndex, int optionNumber) {
        if (agreedDate.isEmpty()) {
            return new SlotChoiceExpansion(
                    "Confirmo o horário " + time + ".",
                    Optional.empty(), Optional.empty(), false, Optional.empty(), 0);
        }
        LocalDate d = agreedDate.get();
        SchedulingEnforcedChoice choice = new SchedulingEnforcedChoice(d, time);
        String iso = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dayPt = d.format(PT_BR_DAY);
        if (selectionByIndex) {
            String hardcoded =
                    "Entendido! Você escolheu a opção "
                            + optionNumber
                            + ", que corresponde ao horário "
                            + time
                            + " do dia "
                            + dayPt
                            + ". Posso confirmar o agendamento?";
            return new SlotChoiceExpansion(
                    hardcoded,
                    Optional.empty(),
                    Optional.of(choice),
                    true,
                    Optional.of(hardcoded),
                    optionNumber);
        }
        String expanded =
                "Confirmo o horário "
                        + time
                        + " para o dia "
                        + iso
                        + ". Use esta data (yyyy-MM-DD: "
                        + iso
                        + ") em create_appointment.";
        return new SlotChoiceExpansion(expanded, Optional.of(choice), Optional.empty(), false, Optional.empty(), 0);
    }

    public static Optional<SchedulingEnforcedChoice> parseLastDraftFromHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            Optional<SchedulingEnforcedChoice> d = extractDraftFromMessage(m.content());
            if (d.isPresent()) {
                return d;
            }
        }
        return Optional.empty();
    }

    private static Optional<SchedulingEnforcedChoice> extractDraftFromMessage(String text) {
        if (text == null || !text.contains(SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
            return Optional.empty();
        }
        String lastDate = null;
        String lastTime = null;
        Matcher mat = DRAFT_ANY.matcher(text);
        while (mat.find()) {
            lastDate = mat.group(1).strip();
            lastTime = mat.group(2).strip();
        }
        if (lastDate == null || lastTime == null) {
            return Optional.empty();
        }
        try {
            LocalDate day = LocalDate.parse(lastDate, DateTimeFormatter.ISO_LOCAL_DATE);
            return Optional.of(new SchedulingEnforcedChoice(day, lastTime));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static List<String> parseLastSlotOptionsFromHistory(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            Optional<String> raw = extractLastAppendixPayload(m.content());
            if (raw.isPresent()) {
                return splitOptions(raw.get());
            }
        }
        return List.of();
    }

    static Optional<LocalDate> parseLastSlotDateFromHistory(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            Optional<LocalDate> d = extractLastSlotDate(m.content());
            if (d.isPresent()) {
                return d;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> extractLastSlotDate(String text) {
        if (text == null || !text.contains(SLOT_DATE_APPENDIX_TOKEN)) {
            return Optional.empty();
        }
        String last = null;
        Matcher mat = SLOT_DATE_ANY.matcher(text);
        while (mat.find()) {
            last = mat.group(1).strip();
        }
        if (last == null || last.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(last, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> extractLastAppendixPayload(String text) {
        if (text == null || !text.contains(SLOT_OPTIONS_APPENDIX_TOKEN)) {
            return Optional.empty();
        }
        String last = null;
        Matcher mat = APPENDIX_ANY.matcher(text);
        while (mat.find()) {
            last = mat.group(1);
        }
        return Optional.ofNullable(last);
    }

    private static List<String> splitOptions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher m = SLOT_OPTION_TIMES_IN_ORDER.matcher(raw);
        while (m.find()) {
            String t = SchedulingSlotCapture.normalizeSingleSlotToken(m.group(1));
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    static Optional<String> resolveChoiceToTime(String msg, List<String> options) {
        if (options.isEmpty() || msg == null || msg.isBlank()) {
            return Optional.empty();
        }
        Matcher tm = TIME.matcher(msg);
        if (tm.find()) {
            String fragment = tm.group(0);
            String canon = SchedulingSlotCapture.normalizeSingleSlotToken(fragment);
            if (canon == null) {
                return Optional.empty();
            }
            if (!options.contains(canon)) {
                return Optional.empty();
            }
            return Optional.of(canon);
        }
        Matcher op = OPCAO.matcher(msg);
        if (op.find()) {
            return pickByOneBasedIndex(Integer.parseInt(op.group(1)), options);
        }
        Matcher solo = SOLO_DIGITS.matcher(msg);
        if (solo.matches()) {
            int n = Integer.parseInt(solo.group(1));
            if (n >= 1 && n <= options.size()) {
                return Optional.of(options.get(n - 1));
            }
        }
        return Optional.empty();
    }

    static int parseOptionNumber(String normalized) {
        if (normalized == null) return 0;
        Matcher op = OPCAO.matcher(normalized);
        if (op.find()) {
            return Integer.parseInt(op.group(1));
        }
        Matcher solo = SOLO_DIGITS.matcher(normalized);
        if (solo.matches()) {
            return Integer.parseInt(solo.group(1));
        }
        return 0;
    }

    private static Optional<String> pickByOneBasedIndex(int oneBased, List<String> options) {
        if (oneBased < 1 || oneBased > options.size()) {
            return Optional.empty();
        }
        return Optional.of(options.get(oneBased - 1));
    }
}
