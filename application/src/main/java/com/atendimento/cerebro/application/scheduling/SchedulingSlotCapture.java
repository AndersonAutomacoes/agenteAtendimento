package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guarda temporariamente os horários livres do último {@code check_availability} no mesmo pedido ao motor Gemini,
 * para o envio WhatsApp (Evolution) poder usar botões.
 */
public final class SchedulingSlotCapture {

    /** Rodapé da lista “premium” (WhatsApp / reforço ao modelo). */
    public static final String SLOT_LIST_FOOTER_PT =
            "Responda com o número da opção desejada (ex.: 3).";

    /**
     * Quando não há vagas ou a lista normalizada fica vazia antes do envio.
     */
    public static final String SLOTS_ALL_OCCUPIED_PT =
            "No momento, todos os horários para esta data estão ocupados. Posso verificar outro dia para você?";

    private static final String[] OPTION_KEYCAP_EMOJI = {
        "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"
    };

    private static final String KEYCAP_0 = "0️⃣";

    private static final ThreadLocal<List<String>> SLOT_TIMES = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<LocalDate> REQUESTED_DATE = new ThreadLocal<>();
    /** Quando definido, substitui o título gerado por {@link #formatChoiceTitle} (payload WhatsApp / Evolution). */
    private static final ThreadLocal<String> INTERACTIVE_MAIN_TEXT = new ThreadLocal<>();

    private static final Pattern TIME_TOKEN = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
    /** HH:mm válido (24h). */
    private static final Pattern HM_STRICT = Pattern.compile("^([01][0-9]|2[0-3]):([0-5][0-9])$");
    private static final DateTimeFormatter PT_BR_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    /** Menção a serviço no histórico/mensagem (para personalizar main_text). */
    private static final Pattern SERVICE_SNIPPET =
            Pattern.compile(
                    "(?i)\\b(alinhamento\\s*3d|alinhamento\\s+3d|alinhamento|revisão\\s+preventiva|revisão|revisao|"
                            + "balanceamento|troca\\s+de\\s+óleo|troca\\s+de\\s+oleo|limpeza\\s+de\\s+bicos|sangria\\s+de\\s+freio)\\b");

    private SchedulingSlotCapture() {}

    public static void clear() {
        SLOT_TIMES.remove();
        REQUESTED_DATE.remove();
        INTERACTIVE_MAIN_TEXT.remove();
    }

    /**
     * Lista limpa: trim, remove vazios, dedupe preservando ordem, formato HH:mm canónico (dois dígitos na hora).
     */
    public static List<String> normalizeSlotTimes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String s : raw) {
            String one = normalizeSingleSlotToken(s);
            if (one != null) {
                ordered.add(one);
            }
        }
        return List.copyOf(ordered);
    }

    /**
     * Prefixo visual: 1️⃣–🔟; a partir de 11 usa keycaps por dígito (ex.: 1️⃣1️⃣, 1️⃣2️⃣), alinhado ao WhatsApp.
     */
    public static String numberedOptionPrefix(int oneBased) {
        if (oneBased < 1) {
            return "";
        }
        if (oneBased <= 10) {
            return OPTION_KEYCAP_EMOJI[oneBased - 1] + " ";
        }
        return multiDigitKeycapPrefix(oneBased) + " ";
    }

    private static String multiDigitKeycapPrefix(int oneBased) {
        String s = String.valueOf(oneBased);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            if (d == 0) {
                sb.append(KEYCAP_0);
            } else {
                sb.append(OPTION_KEYCAP_EMOJI[d - 1]);
            }
        }
        return sb.toString();
    }

    /**
     * Linhas numeradas {@code N) HH:mm} (legível em qualquer cliente; evita emojis duplicados 1️⃣1️⃣ a partir de 11).
     */
    public static String formatNumberedSlotLines(List<String> times) {
        if (times == null || times.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (sb.length() > 3800) {
                sb.append("\n…");
                break;
            }
            sb.append('*')
                    .append(i + 1)
                    .append(") ")
                    .append(times.get(i))
                    .append("*\n");
        }
        return sb.toString().strip();
    }

    /** Título e data (WhatsApp: negrito com {@code *…*}). */
    public static String formatPremiumAvailabilityHeader(LocalDate date, ZoneId calendarZone) {
        if (date == null) {
            return "*Agenda Disponível*\n\n*Data:* —";
        }
        return "*Agenda Disponível*\n\n*Data:* " + date.format(PT_BR_DATE);
    }

    /**
     * Lista “premium” completa: cabeçalho 📅, linhas numeradas com emoji, rodapé. Devolve vazio se não houver horários.
     */
    public static String buildPremiumFormattedSlotList(LocalDate date, ZoneId calendarZone, List<String> times) {
        if (times == null || times.isEmpty()) {
            return "";
        }
        List<String> clean = normalizeSlotTimes(times);
        if (clean.isEmpty()) {
            return "";
        }
        String header = formatPremiumAvailabilityHeader(date, calendarZone);
        String lines = formatNumberedSlotLines(clean);
        return header + "\n\n" + lines + "\n\n" + SLOT_LIST_FOOTER_PT;
    }

    /**
     * Extrai HH:mm de um fragmento (ex.: segmento após vírgula, com espaços ou NBSP).
     */
    /** Visível ao pacote ({@link SchedulingUserReplyNormalizer}). */
    public static String normalizeSingleSlotToken(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip().replace('\u00a0', ' ').replaceAll("\\s+", " ").strip();
        if (t.isEmpty()) {
            return null;
        }
        Matcher m = TIME_TOKEN.matcher(t);
        if (!m.find()) {
            return null;
        }
        String hm = m.group(1);
        String[] p = hm.split(":");
        if (p.length != 2) {
            return null;
        }
        try {
            int h = Integer.parseInt(p[0]);
            int min = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || min < 0 || min > 59) {
                return null;
            }
            String canon = String.format(Locale.ROOT, "%02d:%02d", h, min);
            if (!HM_STRICT.matcher(canon).matches()) {
                return null;
            }
            return canon;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extrai HH:mm da linha devolvida pelo calendário (mock ou Google), incluindo {@code "09:00, 10:00"} após o sufixo
     * {@code Horários livres em ...}.
     */
    public static List<String> parseSlotTimesFromAvailabilityLine(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return List.of();
        }
        if (!containsHorariosLivresMarker(toolResult)) {
            return List.of();
        }
        if (NENHUM_HORARIO_LIVRE.matcher(toolResult).find()) {
            return List.of();
        }
        LinkedHashSet<String> rough = new LinkedHashSet<>();
        Matcher m = TIME_TOKEN.matcher(toolResult);
        while (m.find()) {
            rough.add(m.group(1));
        }
        for (String segment : toolResult.split("[,;\\n]")) {
            Matcher sm = TIME_TOKEN.matcher(segment.strip());
            while (sm.find()) {
                rough.add(sm.group(1));
            }
        }
        return normalizeSlotTimes(new ArrayList<>(rough));
    }

    private static final Pattern HORARIOS_LIVRES = Pattern.compile("(?i)hor[aá]rios\\s+livres");

    private static final Pattern NENHUM_HORARIO_LIVRE = Pattern.compile("(?i)nenhum\\s+hor[aá]rio");

    private static boolean containsHorariosLivresMarker(String s) {
        return s != null && HORARIOS_LIVRES.matcher(s).find();
    }

    /** Regista horários extraídos do retorno textual da ferramenta (ex.: "09:00, 10:00"). */
    public static void setSlotsFromToolResult(String toolResult, LocalDate requestedDate) {
        REQUESTED_DATE.remove();
        INTERACTIVE_MAIN_TEXT.remove();
        List<String> found = parseSlotTimesFromAvailabilityLine(toolResult);
        if (found.isEmpty()) {
            SLOT_TIMES.remove();
            return;
        }
        SLOT_TIMES.set(new ArrayList<>(found));
        if (requestedDate != null) {
            REQUESTED_DATE.set(requestedDate);
        }
    }

    /**
     * Define slots + texto principal do cartão (modelo JSON Evolution). Usado pela ferramenta {@code check_availability}.
     */
    public static void setStructuredAvailability(String mainText, List<String> slotTimes, LocalDate requestedDate) {
        REQUESTED_DATE.remove();
        INTERACTIVE_MAIN_TEXT.remove();
        List<String> clean = normalizeSlotTimes(slotTimes);
        if (clean.isEmpty()) {
            SLOT_TIMES.remove();
            return;
        }
        SLOT_TIMES.set(new ArrayList<>(clean));
        if (requestedDate != null) {
            REQUESTED_DATE.set(requestedDate);
        }
        if (mainText != null && !mainText.isBlank()) {
            INTERACTIVE_MAIN_TEXT.set(mainText.strip());
        }
    }

    public static Optional<String> peekInteractiveMainText() {
        return Optional.ofNullable(INTERACTIVE_MAIN_TEXT.get());
    }

    public static List<String> peekSlotTimes() {
        return Collections.unmodifiableList(new ArrayList<>(SLOT_TIMES.get()));
    }

    /** Data da última consulta de disponibilidade com slots (mesmo pedido). */
    public static Optional<LocalDate> peekRequestedDate() {
        return Optional.ofNullable(REQUESTED_DATE.get());
    }

    /**
     * Título curto para o cartão Evolution (ex.: "Escolha um horário para amanhã:").
     */
    public static String formatChoiceTitle(LocalDate date, ZoneId calendarZone) {
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        if (date == null) {
            return "Escolha um horário:";
        }
        LocalDate today = LocalDate.now(z);
        if (date.equals(today.plusDays(1))) {
            return "Escolha um horário para amanhã:";
        }
        if (date.equals(today)) {
            return "Escolha um horário para hoje:";
        }
        return "Escolha um horário para " + date.format(PT_BR_DATE) + ":";
    }

    /**
     * Monta {@code main_text} do payload WhatsApp (Evolution), alinhado ao JSON estruturado da ferramenta.
     */
    public static String buildWhatsAppMainText(LocalDate date, ZoneId calendarZone, String contextForServiceHint) {
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        LocalDate today = LocalDate.now(z);
        String dateHuman;
        String dateParen;
        if (date == null) {
            dateHuman = "a data escolhida";
            dateParen = "";
        } else if (date.equals(today.plusDays(1))) {
            dateHuman = "amanhã";
            dateParen = " (" + date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))) + ")";
        } else if (date.equals(today)) {
            dateHuman = "hoje";
            dateParen = " (" + date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("pt-BR"))) + ")";
        } else {
            dateHuman = date.format(PT_BR_DATE);
            dateParen = "";
        }
        String service = extractServiceSnippet(contextForServiceHint);
        if (service != null && !service.isBlank()) {
            return "Selecione uma opção de horário para "
                    + service
                    + " "
                    + dateHuman
                    + dateParen
                    + ":";
        }
        return "Selecione uma opção de horário para " + dateHuman + dateParen + ":";
    }

    private static String extractServiceSnippet(String blob) {
        if (blob == null || blob.isBlank()) {
            return null;
        }
        Matcher m = SERVICE_SNIPPET.matcher(blob);
        if (!m.find()) {
            return null;
        }
        return m.group(1).trim();
    }

    public static Optional<WhatsAppInteractiveReply> takeWhatsAppInteractive(
            String assistantDescriptionFallback, ZoneId calendarZone) {
        List<String> times = SLOT_TIMES.get();
        LocalDate date = REQUESTED_DATE.get();
        String mainOverride = INTERACTIVE_MAIN_TEXT.get();
        SLOT_TIMES.remove();
        REQUESTED_DATE.remove();
        INTERACTIVE_MAIN_TEXT.remove();
        times = times == null ? List.of() : normalizeSlotTimes(times);
        if (times.isEmpty()) {
            return Optional.empty();
        }
        String title =
                mainOverride != null && !mainOverride.isBlank()
                        ? mainOverride
                        : formatChoiceTitle(date, calendarZone);
        String desc;
        if (mainOverride != null && !mainOverride.isBlank()) {
            desc = "Toque num botão para confirmar o horário.";
        } else if (assistantDescriptionFallback != null && !assistantDescriptionFallback.isBlank()) {
            desc = assistantDescriptionFallback.strip();
        } else {
            desc = formatNumberedSlotLines(times) + "\n\n" + SLOT_LIST_FOOTER_PT;
        }
        if (desc.length() > 1024) {
            desc = desc.substring(0, 1021) + "…";
        }
        return Optional.of(new WhatsAppInteractiveReply(title, desc, times, "", date));
    }
}
