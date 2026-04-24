package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private static final Pattern LOOSE_BR_DATE =
            Pattern.compile("\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?");

    private static final DateTimeFormatter PT_BR_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private SchedulingUserReplyNormalizer() {}

    /** Índice 1…N quando a mensagem é só dígitos ou «opção N». */
    public static Optional<Integer> tryParseOptionIndexFromUserMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String n = userMessage.strip();
        Matcher solo = SOLO_DIGITS.matcher(n);
        if (solo.matches()) {
            return Optional.of(Integer.parseInt(solo.group(1)));
        }
        Matcher op = OPCAO.matcher(n);
        if (op.find()) {
            return Optional.of(Integer.parseInt(op.group(1)));
        }
        return Optional.empty();
    }

    /**
     * Extrai a última lista {@code [slot_options:…]} de um texto (ex.: transcript), na ordem em que foi mostrada ao
     * cliente.
     */
    public static List<String> parseLastSlotOptionsFromTranscript(String text) {
        if (text == null || !text.contains(SLOT_OPTIONS_APPENDIX_TOKEN)) {
            return List.of();
        }
        String last = null;
        Matcher mat = APPENDIX_ANY.matcher(text);
        while (mat.find()) {
            last = mat.group(1);
        }
        if (last == null) {
            return List.of();
        }
        return splitOptions(last);
    }

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

    /**
     * Remove só marcações de agendamento por slots (mantém {@code [cancel_option_map:…]} — necessário na sessão até
     * cancelar).
     */
    public static String stripSlotSchedulingStateOnly(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String s = content;
        s = s.replaceAll("(?s)\\[slot_options:[^\\]]+\\]", "");
        s = s.replaceAll("(?s)\\[slot_date:[^\\]]+\\]", "");
        s = s.replaceAll("(?s)\\[scheduling_draft:[^\\]]+\\]", "");
        // Não usar \\s* entre * — remove *\\n* e cola blocos WhatsApp na mesma linha.
        s = s.replaceAll("\\*{1,2}[ \\t]*\\*{1,2}", "");
        s = s.replaceAll("(?s)\\n{3,}", "\n\n");
        return s.strip();
    }

    /**
     * Remove apêndices internos antes de expor texto ao cliente (WhatsApp). Inclui {@code [cancel_option_map:…]}.
     */
    public static String stripInternalSlotAppendix(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String s = stripSlotSchedulingStateOnly(content);
        s = s.replaceAll("(?is)\\[cancel_option_map:[^\\]]+\\]", "");
        s = s.replaceAll("(?s)\\n{3,}", "\n\n");
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
        if (looksLikeRescheduleOrTimeChangeIntent(userMessage)) {
            return SlotChoiceExpansion.unchanged(userMessage);
        }
        String normalized =
                Normalizer.normalize(userMessage == null ? "" : userMessage.strip(), Normalizer.Form.NFKC);

        if (tryParseOptionIndexFromUserMessage(normalized).isPresent()
                && lastAssistantSuggestedAppointmentCancellation(history)) {
            return SlotChoiceExpansion.unchanged(userMessage);
        }

        if (looksLikeConfirmation(normalized) && lastAssistantSuggestedAppointmentCancellation(history)) {
            return SlotChoiceExpansion.unchanged(userMessage);
        }

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

    /**
     * Rascunho na mensagem mais recente do assistente (bot) que ainda contém {@code [scheduling_draft:…]} — ignora
     * mensagens bot posteriores sem esse token (ex.: reforço curto) e procura para trás até encontrar o rascunho.
     */
    public static Optional<SchedulingEnforcedChoice> parseLastDraftFromHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            String content = m.content();
            if (content == null || !content.contains(SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
                continue;
            }
            if (isStaleSlotStateSupersededByCompletedBooking(history, i)) {
                return Optional.empty();
            }
            return extractDraftFromMessage(content);
        }
        return Optional.empty();
    }

    /**
     * O {@link com.atendimento.cerebro.application.service.ChatService} substitui a mensagem do utilizador («sim») por
     * esta instrução quando o backend já fixou data/hora após {@code [scheduling_draft:…]} — permite atalho sem chamada
     * ao modelo.
     */
    public static boolean isBackendCreateConfirmationInstruction(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String u = userMessage.strip();
        return u.contains("O cliente confirmou o agendamento") && u.contains("create_appointment");
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
        int slotListIdx = indexOfLastBotAssistantWithSlotOptions(history);
        if (slotListIdx < 0) {
            return List.of();
        }
        if (isStaleSlotStateSupersededByCompletedBooking(history, slotListIdx)) {
            return List.of();
        }
        Optional<String> raw = extractLastAppendixPayload(history.get(slotListIdx).content());
        return raw.map(SchedulingUserReplyNormalizer::splitOptions).orElseGet(List::of);
    }

    private static int indexOfLastBotAssistantWithSlotOptions(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            if (extractLastAppendixPayload(m.content()).isPresent()) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfLastBotAssistantWithCompletedBooking(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            if (assistantMessageIndicatesCompletedScheduling(m.content())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isStaleSlotStateSupersededByCompletedBooking(List<Message> history, int slotListIdx) {
        int completedIdx = indexOfLastBotAssistantWithCompletedBooking(history);
        return completedIdx > slotListIdx;
    }

    /** Indica se a mensagem do assistente já reflecte um agendamento criado (card, texto de sucesso da ferramenta). */
    public static boolean assistantMessageIndicatesCompletedScheduling(String content) {
        return SchedulingCreateAppointmentResult.historyTextIndicatesSuccessfulBooking(content);
    }

    /** Última data {@code [slot_date:yyyy-MM-DD]} nas mensagens do assistente (do mais recente ao mais antigo). */
    public static Optional<LocalDate> parseLastSlotDateFromHistory(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            Optional<LocalDate> d = extractLastSlotDate(m.content());
            if (d.isPresent()) {
                if (isStaleSlotStateSupersededByCompletedBooking(history, i)) {
                    return Optional.empty();
                }
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

    private static final Pattern CANCELLATION_INTENT =
            Pattern.compile(
                    "(?is).*(cancelar|cancela|cancelamento|desmarcar|desmarca|anular|anula|desagendar|desmarcação).*");

    /**
     * Parte do transcript após a última confirmação de cancelamento bem-sucedido — evita que «cancelar» antigo bloqueie
     * {@code check_availability} quando o cliente já voltou a agendar.
     */
    public static String transcriptAfterLastCancellationSuccess(String blob) {
        if (blob == null || blob.isBlank()) {
            return "";
        }
        String p1 = AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX;
        String p2 = AppointmentService.CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX;
        int i1 = blob.lastIndexOf(p1);
        int i2 = blob.lastIndexOf(p2);
        if (i1 < 0 && i2 < 0) {
            return blob.strip();
        }
        if (i1 >= i2) {
            return blob.substring(i1 + p1.length()).strip();
        }
        return blob.substring(i2 + p2.length()).strip();
    }

    /**
     * Mensagem actual indica consulta de horário / data (mesmo que o histórico ainda mencione cancelamento antigo).
     */
    public static boolean userMessageOverridesCancelForAvailabilityCheck(String latestUserMessage) {
        if (latestUserMessage == null || latestUserMessage.isBlank()) {
            return false;
        }
        String s = latestUserMessage.strip();
        if (looksLikeRescheduleOrTimeChangeIntent(s)) {
            return false;
        }
        if (looksLikeCancellationIntent(s)) {
            return false;
        }
        if (looksLikeSchedulingRestartIntent(s)) {
            return true;
        }
        if (TIME.matcher(s).find()) {
            return true;
        }
        if (LOOSE_BR_DATE.matcher(s).find()) {
            return true;
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (n.contains("amanh") || n.contains("ananh")) {
            return true;
        }
        if (n.contains("depois") && n.contains("amanh")) {
            return true;
        }
        return false;
    }

    /**
     * Se {@code true}, a ferramenta {@code check_availability} não deve prosseguir — o cliente está a cancelar ou o
     * contexto (cortado após último cancelamento concluído) ainda é só de cancelamento.
     */
    public static boolean shouldRefuseAvailabilityBecauseCancelIntent(String transcriptHint, String latestUserMessage) {
        String latest = latestUserMessage == null ? "" : latestUserMessage.strip();
        if (userMessageOverridesCancelForAvailabilityCheck(latest)) {
            return false;
        }
        String blob =
                transcriptHint == null || transcriptHint.isBlank()
                        ? latest
                        : transcriptHint.strip() + "\n" + latest;
        if (looksLikeCancellationIntent(latest)) {
            return true;
        }
        String scoped = transcriptAfterLastCancellationSuccess(blob);
        return looksLikeCancellationInBlob(scoped);
    }

    /**
     * Cancelamento explícito no texto — sem «remover/excluir» isolados (evita oficina: «remover pneus», «remoção de
     * filtro» no pitch de serviços).
     */
    private static final Pattern CANCELLATION_BLOB_CORE =
            Pattern.compile(
                    "(cancelar|cancelamento|cancela(ç|c)ão|desmarcar|desmarca|anular|anula|desagendar|desmarca(ç|c)ão)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * «Excluir/remover/remoção/exclusão» só contam quando ligados a agendamento ou equivalente (nunca «remover»
     * sozinho no meio de descrição de serviço).
     */
    private static final Pattern CANCELLATION_BLOB_REMOVE_OR_EXCLUDE_NEAR_SCHEDULING =
            Pattern.compile(
                    "(?is)(?:\\b(excluir|remover)\\b[\\s\\S]{0,96}\\b(agendamentos?|compromissos?|marca(ç|c)ões?|marca(ç|c)ao|horários?|horarios?|consultas?)\\b"
                            + "|\\b(agendamentos?|compromissos?|marca(ç|c)ões?|marca(ç|c)ao|horários?|horarios?|consultas?)[\\s\\S]{0,96}\\b(excluir|remover)\\b"
                            + "|\\bremo(ç|c)ão\\b[\\s\\S]{0,72}\\b(de\\s+)?(o\\s+|a\\s+)?(agendamento|compromisso|marca(ç|c)ão|consulta)\\b"
                            + "|\\b(exclusão|exclusao)\\b[\\s\\S]{0,72}\\b(de\\s+)?(o\\s+|a\\s+)?(agendamento|compromisso|consulta)\\b)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Confirmações curtas (ex.: «sim») no fluxo de agendamento/cancelamento — não contar como «reinício» para marcar
     * horário.
     */
    private static final Pattern SHORT_SCHEDULING_CONFIRM =
            Pattern.compile("^(sim|sí|ok|confirmado|confirmo|pode|isso|perfeito|fechado)[.!\\s]*$", Pattern.CASE_INSENSITIVE);

    /**
     * O utilizador voltou a pedir marcação de horário após um fluxo de cancelamento — sair do modo lista/cancelar e
     * limpar apêndices internos do histórico enviado ao modelo.
     */
    public static boolean looksLikeSchedulingRestartIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String stripped = userMessage.strip();
        if (looksLikeCancellationIntent(stripped)) {
            return false;
        }
        if (stripped.length() <= 24 && SHORT_SCHEDULING_CONFIRM.matcher(stripped).matches()) {
            return false;
        }
        String n = Normalizer.normalize(stripped, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (n.contains("desagendar")) {
            return false;
        }
        if (Pattern.compile("\\b(agendar|reagendar)\\b").matcher(n).find()) {
            return true;
        }
        if (Pattern.compile("\\bmarcar\\b").matcher(n).find()) {
            return true;
        }
        if (n.contains("marcação") || n.contains("marcacao")) {
            return true;
        }
        if (n.contains("horário") || n.contains("horario")) {
            return true;
        }
        if (n.contains("dispon")) {
            return true;
        }
        if (Pattern.compile("\\bvagas?\\b").matcher(n).find()) {
            return true;
        }
        return n.contains("calend");
    }

    /**
     * Indica que o utilizador pretende cancelar/desmarcar um agendamento (fluxo distinto de marcar horário).
     */
    public static boolean looksLikeCancellationIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        return CANCELLATION_INTENT.matcher(userMessage.strip()).matches();
    }

    /**
     * Cliente quer trocar/remarcar um horário já existente (cancelar + novo agendamento em sequência), não um pedido
     * genérico de «ver vagas».
     */
    public static boolean looksLikeRescheduleOrTimeChangeIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        if (looksLikeCancellationIntent(userMessage.strip())) {
            return false;
        }
        String n = Normalizer.normalize(userMessage.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\b(reagendar|reagendamento|remarcar)\\b").matcher(n).find()) {
            return true;
        }
        boolean changeVerb = Pattern.compile("\\b(trocar|alterar|mudar)\\b").matcher(n).find();
        boolean topic =
                n.contains("horário")
                        || n.contains("horario")
                        || n.contains("marcação")
                        || n.contains("marcacao")
                        || n.contains("agendamento");
        if (changeVerb && topic) {
            return true;
        }
        return changeVerb && Pattern.compile("\\b(às|as)\\s+\\d").matcher(n).find();
    }

    /**
     * Cliente pede ver/listar agendamentos ou compromissos já existentes (distinto de marcar novo horário ou pedir
     * disponibilidade genérica). Usado para forçar {@code get_active_appointments} quando o modelo não devolve texto.
     */
    public static boolean looksLikeListActiveAppointmentsIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        if (looksLikeRescheduleOrTimeChangeIntent(userMessage)) {
            return false;
        }
        String n = Normalizer.normalize(userMessage.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (n.length() > 200) {
            return false;
        }
        if (n.contains("meus agend")
                || n.contains("minhas consultas")
                || n.contains("minhas marca")
                || n.contains("todos os agend")
                || n.contains("todas as consultas")) {
            return true;
        }
        boolean subject =
                n.contains("agendamento")
                        || n.contains("agendamentos")
                        || n.contains("compromisso")
                        || n.contains("compromissos")
                        || n.contains("marcação")
                        || n.contains("marcações")
                        || n.contains("marcacao")
                        || n.contains("marcacoes");
        if (!subject) {
            return false;
        }
        if (n.contains("quais ") && (n.contains("tenho") || n.contains("são") || n.contains("sao "))) {
            return true;
        }
        boolean listVerb =
                n.contains("listar")
                        || n.contains("lista de")
                        || (n.contains("lista ") && !n.contains("lista de servi"))
                        || n.contains("mostrar")
                        || n.contains("mostre")
                        || n.contains("exibir")
                        || (n.contains("ver ")
                                && (n.contains("meu")
                                        || n.contains("os ")
                                        || n.contains("a lista")
                                        || n.contains("minha")
                                        || n.contains("minhas")))
                        || (n.contains("consultar") && n.contains("agend"));
        return listVerb;
    }

    /**
     * @deprecated Preferir {@link #RESCHEDULE_SYSTEM_BLOCK} no system prompt (não prefixar a mensagem do utilizador).
     */
    @Deprecated
    public static final String RESCHEDULE_FLOW_HINT =
            "[Instrução interna — reagendar] O cliente quer trocar/mudar um horário já marcado. Fluxo obrigatório: "
                    + "(1) get_active_appointments para identificar o compromisso; (2) cancel_appointment com o ID "
                    + "correcto e confirmar sucesso; (3) só depois check_availability para a nova data; "
                    + "(4) create_appointment após confirmação. Não mostre só horários livres sem tratar o agendamento "
                    + "anterior quando o pedido for trocar/remarcar/reagendar.";

    /**
     * Instrução para o system prompt em intenção de reagendar (não ecoa no WhatsApp).
     */
    public static final String RESCHEDULE_SYSTEM_BLOCK =
            "Reagendar/remarcar: primeiro valide a disponibilidade do horário pretendido (check_availability). "
                    + "Se o horário desejado estiver disponível, cancele o agendamento anterior correcto com "
                    + "cancel_appointment e só então conclua com create_appointment. Se não estiver disponível, informe "
                    + "que o horário actual foi mantido e peça para escolher uma opção da lista disponível.";

    private static final DateTimeFormatter BR_SLASH_DATE = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT);

    /** "24/04/2026 11:00 para as 15:00" ou "dia 24/04/2026, 11:00 para 15:00" */
    private static final Pattern REAG_DATA_HORA_PARA_HORA =
            Pattern.compile(
                    "(\\d{1,2}/\\d{1,2}/\\d{4})[\\s,]+(\\d{1,2}:\\d{2})\\D{0,8}para(?:\\s+as?)?\\s*(\\d{1,2}:\\d{2})",
                    Pattern.CASE_INSENSITIVE);
    /** "amanhã às 11:00 para as 15:00", "hoje 09:00 para 10:30" */
    private static final Pattern REAG_RELATIVE_DIA_HORA_PARA_HORA =
            Pattern.compile(
                    "(hoje|amanh[ãa])\\D{0,12}(\\d{1,2}:\\d{2}).{0,80}?para(?:\\s+as?)?\\D{0,12}(?:hoje|amanh[ãa])?\\D{0,8}(\\d{1,2}:\\d{2})",
                    Pattern.CASE_INSENSITIVE);
    /** Variante livre com "amanhã/hoje ... das 11:00 para as 12:00" e texto intermédio. */
    private static final Pattern REAG_RELATIVE_DIA_DAS_PARA =
            Pattern.compile(
                    "(hoje|amanh[ãa]).{0,40}?(\\d{1,2}:\\d{2}).{0,120}?para(?:\\s+as?)?.{0,20}?(\\d{1,2}:\\d{2})",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Extrai a data e o horário de origem de um pedido de reagendamento (o slot a cancelar), quando o cliente
     * indicou "dd/MM/aaaa HH:mm para HH:mm". Usado para pré-cancelar o compromisso correcto com vários agendamentos
     * activos.
     */
    public static Optional<ReagendamentoDeParaHint> parseReagendamentoDeParaHint(String userMessage) {
        return parseReagendamentoDeParaHint(userMessage, ZoneId.systemDefault());
    }

    public static Optional<ReagendamentoDeParaHint> parseReagendamentoDeParaHint(
            String userMessage, ZoneId calendarZone) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String s = userMessage.strip();
        Matcher m1 = REAG_DATA_HORA_PARA_HORA.matcher(s);
        if (m1.find()) {
            return parseHintWithDate(m1.group(1), m1.group(2), m1.group(3));
        }
        Matcher m2 = REAG_RELATIVE_DIA_HORA_PARA_HORA.matcher(s);
        if (m2.find()) {
            String token = m2.group(1);
            ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
            LocalDate base = LocalDate.now(z);
            LocalDate day = token != null && token.toLowerCase(Locale.ROOT).startsWith("amanh")
                    ? base.plusDays(1)
                    : base;
            return parseHintWithLocalDate(day, m2.group(2), m2.group(3));
        }
        Matcher m3 = REAG_RELATIVE_DIA_DAS_PARA.matcher(s);
        if (m3.find()) {
            String token = m3.group(1);
            ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
            LocalDate base = LocalDate.now(z);
            LocalDate day = token != null && token.toLowerCase(Locale.ROOT).startsWith("amanh")
                    ? base.plusDays(1)
                    : base;
            return parseHintWithLocalDate(day, m3.group(2), m3.group(3));
        }
        return Optional.empty();
    }

    private static Optional<ReagendamentoDeParaHint> parseHintWithDate(
            String dateStr, String fromTimeStr, String toTimeStr) {
        if (dateStr == null || fromTimeStr == null || toTimeStr == null) {
            return Optional.empty();
        }
        try {
            LocalDate day = LocalDate.parse(dateStr, BR_SLASH_DATE);
            return parseHintWithLocalDate(day, fromTimeStr, toTimeStr);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<ReagendamentoDeParaHint> parseHintWithLocalDate(
            LocalDate day, String fromTimeStr, String toTimeStr) {
        if (day == null || fromTimeStr == null || toTimeStr == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new ReagendamentoDeParaHint(
                            day, parseBrLocalTime(fromTimeStr.strip()), parseBrLocalTime(toTimeStr.strip())));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static LocalTime parseBrLocalTime(String t) {
        if (t == null || t.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        String x = t.strip();
        try {
            return LocalTime.parse(x, DateTimeFormatter.ofPattern("H:mm", Locale.ROOT));
        } catch (DateTimeParseException e1) {
            return LocalTime.parse(x, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
        }
    }

    /**
     * {@code true} se o texto (mensagem única ou bloco com histórico) contiver intenção de cancelar, excluir ou
     * remover agendamento.
     */
    public static boolean looksLikeCancellationInBlob(String blob) {
        if (blob == null || blob.isBlank()) {
            return false;
        }
        String n = Normalizer.normalize(blob.strip(), Normalizer.Form.NFKC);
        return CANCELLATION_BLOB_CORE.matcher(n).find()
                || CANCELLATION_BLOB_REMOVE_OR_EXCLUDE_NEAR_SCHEDULING.matcher(n).find();
    }

    /**
     * A última mensagem do assistente no histórico parece a lista devolvida por {@code get_active_appointments}
     * (evita que «1» seja interpretado como escolha de horário de {@code check_availability}).
     */
    public static boolean lastAssistantSuggestedAppointmentCancellation(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT) {
                continue;
            }
            String c = m.content();
            if (c == null || c.isBlank()) {
                return false;
            }
            if (c.contains(CancelOptionMap.APPENDIX_PREFIX)) {
                return true;
            }
            String lower = c.toLowerCase(Locale.ROOT);
            return lower.contains("agendamentos agendado")
                    || lower.contains("agendamentos ativos")
                    || lower.contains("agendamentos activos")
                    || lower.contains("*agendamentos*")
                    || lower.contains("quais dos atendimentos abaixo")
                    || lower.contains("segue o seu agendamento ativo")
                    || lower.contains("opções entre os serviços agendados")
                    || lower.contains("opcoes entre os servicos agendados")
                    || lower.contains("pergunte qual deseja cancelar")
                    || lower.contains("serviços agendados")
                    || lower.contains("servicos agendados");
        }
        return false;
    }

    /**
     * Confirmação de cancelamento efectivo no texto do assistente (usa {@code contains} para tolerar prefixos do
     * modelo ou formatação antes da frase fixa de sucesso).
     */
    public static boolean assistantMessageIndicatesSuccessfulCancellation(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains(AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX)
                || content.contains(AppointmentService.CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX);
    }

    /**
     * Índice da última mensagem do assistente que indica cancelamento concluído com sucesso; {@code -1} se não houver.
     */
    public static int indexOfLastAssistantSuccessfulCancellation(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return -1;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() == MessageRole.ASSISTANT && assistantMessageIndicatesSuccessfulCancellation(m.content())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A última mensagem de assistente no histórico é confirmação de cancelamento bem-sucedido (ver
     * {@link #assistantMessageIndicatesSuccessfulCancellation(String)}).
     */
    public static boolean lastAssistantMessageIndicatesSuccessfulCancellation(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT) {
                continue;
            }
            return assistantMessageIndicatesSuccessfulCancellation(m.content());
        }
        return false;
    }

    /**
     * Remove {@code [slot_options:…]}, {@code [slot_date:…]} e {@code [scheduling_draft:…]} de cada mensagem (cópia
     * nova) — usado quando o cliente pede cancelamento para não manter rascunho de novo agendamento na sessão.
     * Preserva {@code [cancel_option_map:…]} para o modelo resolver opção → ID.
     */
    public static List<Message> stripSchedulingStateFromHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            String stripped = stripSlotSchedulingStateOnly(m.content());
            if (stripped == null || stripped.isBlank()) {
                stripped = "…";
            }
            out.add(new Message(m.role(), stripped.strip(), m.timestamp(), m.senderType()));
        }
        return out;
    }

    /**
     * Remove apêndices internos de agendamento e de cancelamento ({@code stripInternalSlotAppendix}) de cada mensagem —
     * usado quando o cliente retoma o fluxo de marcar horário após tentativa de cancelamento.
     */
    public static List<Message> stripInternalAppendicesFromHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            String stripped = stripInternalSlotAppendix(m.content());
            if (stripped == null || stripped.isBlank()) {
                stripped = "…";
            }
            out.add(new Message(m.role(), stripped.strip(), m.timestamp(), m.senderType()));
        }
        return out;
    }
}
