package com.atendimento.cerebro.application.scheduling;

import java.util.regex.Pattern;

/**
 * Remove segmentos {@code [...]} da mensagem visível ao utilizador (tags internas do assistente / modelo).
 *
 * <p>Inclui {@code [cancel_option_map:…]}, {@code [slot_options:…]} e similares — o cliente nunca deve ver apêndices
 * técnicos. O histórico persistido para o modelo continua completo em {@code conversation_message}; só o envio WhatsApp
 * passa por aqui (sanitização na rota outbound).
 */
public final class AssistantOutputSanitizer {

    /** URLs do evento no Google Calendar — nunca mostrar ao cliente final (WhatsApp). */
    private static final Pattern GOOGLE_CALENDAR_EVENT_URL =
            Pattern.compile("https?://\\S*google\\.com/calendar\\S*", Pattern.CASE_INSENSITIVE);

    private static final Pattern CALENDAR_GOOGLE_HOST_URL =
            Pattern.compile("https?://calendar\\.google\\.com\\S*", Pattern.CASE_INSENSITIVE);

    /**
     * Texto injectado em {@code SlotChoiceExpansion} quando o utilizador confirma «sim» — não é mensagem para o
     * cliente.
     */
    private static final Pattern INTERNAL_CLIENT_CONFIRM_AND_CHAME_CREATE =
            Pattern.compile(
                    "(?is)(?:\\*{0,2}\\s*)?O\\s+cliente\\s+confirmou\\s+o\\s+agendamento\\.?\\s*(?:\\*{0,2}\\s*)?"
                            + "Chame\\s+create_appointment\\s+com\\s+date\\s*=\\s*\\d{4}-\\d{2}-\\d{2}\\s+"
                            + "e\\s+time\\s*=\\s*\\d{1,2}:\\d{2}\\s*\\.?(?:\\*{0,2})?");

    /** Texto de expansão em {@code SchedulingUserReplyNormalizer#buildSlotExpansion} (confirmação de horário). */
    private static final Pattern USE_ESTA_DATA_EM_CREATE =
            Pattern.compile("(?is)\\.?\\s*Use\\s+esta\\s+data\\s*\\([^)]*\\)\\s+em\\s+create_appointment\\.?");

    /** Linhas que são só instruções de ferramenta para o modelo. */
    private static final Pattern LINE_START_CHAME_TOOL =
            Pattern.compile(
                    "(?im)^\\s*Chame\\s+(?:check_availability|create_appointment|get_active_appointments|cancel_appointment)\\b.*$");

    /**
     * Instruções internas com prefixo entre colchetes (eco do system ou do backend) — nunca mostrar ao cliente.
     */
    private static final Pattern BRACKETED_INTERNAL_PREFIX =
            Pattern.compile(
                    "(?m)^\\s*\\[(?:Instru[çc]ão\\s+interna|Refor[çc]o\\s+do\\s+sistema|Gest[ãa]o\\s+de\\s+agendamentos\\s+existentes)[^\\]]*\\]\\s*\\n*");

    /** Rasto legado: linha a avisar o modelo, não o utilizador. */
    private static final Pattern LINE_AGENT_META_CONFLICT =
            Pattern.compile(
                    "(?im)^\\s*Conflito\\s+de\\s+hor[áa]rio:.+?(sem\\s+mencionar|Explique|cordialidade\\s+ao\\s+cliente).*$");

    private AssistantOutputSanitizer() {}

    /**
     * Texto seguro para WhatsApp: remove apêndices conhecidos ({@link SchedulingUserReplyNormalizer#stripInternalSlotAppendix})
     * e qualquer outro bloco {@code [...]}.
     */
    public static String stripSquareBracketSegments(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = SchedulingUserReplyNormalizer.stripInternalSlotAppendix(text);
        String prev;
        do {
            prev = s;
            s = s.replaceAll("\\[[^\\]]*\\]", "");
        } while (!s.equals(prev));
        s = s.replaceAll("[ \t]{2,}", " ");
        // Não usar \\s* entre * — abrangeria \\n e colaria blocos WhatsApp (*...*\\n*...*) numa só linha.
        s = s.replaceAll("\\*{1,2}[ \\t]*\\*{1,2}", "");
        s = s.replaceAll("(?s)\\n{3,}", "\n\n");
        s = stripGoogleCalendarUrlsFromCustomerMessage(s.strip());
        return stripInternalAgentDirectivesForCustomer(s);
    }

    /**
     * Remove instruções internas (ferramentas, prompts) que o modelo ou o backend possam ecoar na mensagem visível.
     * Deve ser aplicado a qualquer texto enviado ao cliente (WhatsApp, etc.).
     */
    public static String stripInternalAgentDirectivesForCustomer(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text.strip();
        }
        String s = INTERNAL_CLIENT_CONFIRM_AND_CHAME_CREATE.matcher(text).replaceAll("");
        s = USE_ESTA_DATA_EM_CREATE.matcher(s).replaceAll("");
        s = LINE_START_CHAME_TOOL.matcher(s).replaceAll("");
        s = BRACKETED_INTERNAL_PREFIX.matcher(s).replaceAll("");
        s = LINE_AGENT_META_CONFLICT.matcher(s).replaceAll("");
        s = s.replaceAll("(?im)^\\s*Pe[çc]a\\s+ao\\s+cliente\\b.*$", "");
        s = s.replaceAll("(?im)^\\s*Diga\\s+ao\\s+cliente\\b.*$", "");
        s = s.replaceAll("(?im)^\\s*Explique\\s+com\\s+cordialidade\\b.*$", "");
        s = s.replaceAll("(?i)dispon[ií]vel\\s+para\\s+Posso\\s", "disponível. Posso ");
        s = s.replaceAll("\\*{1,2}[ \\t]*\\*{1,2}", " ");
        s = s.replaceAll("[ \t]{2,}", " ");
        s = s.replaceAll("(?s)\\n{3,}", "\n\n");
        return s.strip();
    }

    /**
     * Remove hiperligações para eventos no Google Calendar que possam vir do modelo ou de retornos antigos da API.
     */
    public static String stripGoogleCalendarUrlsFromCustomerMessage(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text.strip();
        }
        String t = GOOGLE_CALENDAR_EVENT_URL.matcher(text).replaceAll("");
        t = CALENDAR_GOOGLE_HOST_URL.matcher(t).replaceAll("");
        t = t.replaceAll("(?i)\\bLink\\s*:\\s*", "");
        // Preserve intentional line breaks for WhatsApp readability.
        t = t.replaceAll("[ \\t]{2,}", " ");
        t = t.replaceAll("[ \\t]*\\n[ \\t]*", "\n");
        t = t.replaceAll("(?s)\\n{3,}", "\n\n");
        t = t.strip();
        return t;
    }
}
