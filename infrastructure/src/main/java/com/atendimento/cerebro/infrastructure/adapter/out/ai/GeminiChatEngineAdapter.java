package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationCardFormatter;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationDetails;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.SenderType;
import com.atendimento.cerebro.infrastructure.config.CerebroAppointmentConfirmationProperties;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

/**
 * Motor de chat Google Gemini (Spring AI). O bean é registado em
 * {@link com.atendimento.cerebro.infrastructure.config.GeminiChatEngineAutoConfiguration} (não como
 * {@code @Component}) para correr depois do auto-config que cria {@code googleGenAiChatModel}.
 */
public class GeminiChatEngineAdapter {

    public static final String DEFAULT_MODEL = "gemini-1.5-flash";

    private static final Logger LOG = LoggerFactory.getLogger(GeminiChatEngineAdapter.class);

    private static final String SCHEDULING_TOOL_RETRY_SUFFIX =
            "\n\n[Reforço do sistema] Se o utilizador pede horários, disponibilidade ou «quais horários», ou envia só uma "
                    + "data (ex.: 13/04 ou 2026-04-13) para marcar, invoque APENAS check_availability com a data em "
                    + "yyyy-MM-DD (respeitando o dia e mês que o cliente indicou). "
                    + "NÃO invoque create_appointment neste turno. create_appointment só quando o cliente escolheu um "
                    + "horário concreto (ou confirmou um horário já listado) noutro turno.";

    private static final Pattern SHORT_SCHEDULING_CONFIRM =
            Pattern.compile("^(sim|sí|ok|confirmado|confirmo|pode|isso|perfeito|fechado)[.!\\s]*$", Pattern.CASE_INSENSITIVE);

    private final GoogleGenAiChatModel chatModel;
    private final String chatModelName;
    private final AppointmentSchedulingPort appointmentSchedulingPort;
    private final CerebroGoogleCalendarProperties calendarProperties;

    private final CerebroAppointmentConfirmationProperties appointmentConfirmationProperties;

    public GeminiChatEngineAdapter(
            @Qualifier("googleGenAiChatModel") GoogleGenAiChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-1.5-flash}") String chatModelName,
            AppointmentSchedulingPort appointmentSchedulingPort,
            CerebroGoogleCalendarProperties calendarProperties,
            CerebroAppointmentConfirmationProperties appointmentConfirmationProperties) {
        this.chatModel = chatModel;
        this.chatModelName = chatModelName != null && !chatModelName.isBlank() ? chatModelName : DEFAULT_MODEL;
        this.appointmentSchedulingPort = appointmentSchedulingPort;
        this.calendarProperties = calendarProperties;
        this.appointmentConfirmationProperties = appointmentConfirmationProperties;
    }

    public AICompletionResponse complete(AICompletionRequest request) {
        String systemContent = buildSystemContent(request);

        if (request.schedulingToolsEnabled()) {
            ZoneId calendarZone = ZoneId.of(calendarProperties.getZone());
            try {
                SchedulingSlotCapture.clear();
                GeminiSchedulingTools tools =
                        new GeminiSchedulingTools(
                                request.tenantId(),
                                request.conversationId(),
                                appointmentSchedulingPort,
                                calendarZone,
                                request.userMessage(),
                                mergeRecentTranscriptForDate(request),
                                request.schedulingEnforcedChoice(),
                                request.schedulingBlockCreateAppointment());
                ChatClientResponse clientResponse = invokeSchedulingChat(systemContent, request, tools);
                int afterFirst = tools.schedulingToolInvocationCount();
                if (afterFirst == 0 && likelySchedulingOrConfirmationTurn(request.userMessage())) {
                    LOG.warn(
                            "Gemini (agendamento): nenhuma ferramenta executada (Java) na 1ª tentativa (tenant={}); a repetir com reforço.",
                            request.tenantId().value());
                    clientResponse = invokeSchedulingChat(systemContent + SCHEDULING_TOOL_RETRY_SUFFIX, request, tools);
                    if (tools.schedulingToolInvocationCount() == 0) {
                        LOG.warn(
                                "Gemini (agendamento): após reforço ainda sem execução de ferramentas (tenant={}). "
                                        + "O modelo não chamou check_availability/create_appointment; teste GEMINI_CHAT_MODEL=gemini-2.0-flash ou gemini-1.5-flash.",
                                request.tenantId().value());
                    }
                } else if (afterFirst == 0) {
                    LOG.debug(
                            "Gemini (agendamento): sem ferramentas neste turno (tenant={}); mensagem tratada como não agendamento.",
                            request.tenantId().value());
                }
                maybeBackfillAvailabilityFromCalendar(request, calendarZone);
                String content = textFromChatResponse(clientResponse.chatResponse());
                if (!SchedulingSlotCapture.peekSlotTimes().isEmpty()) {
                    content =
                            enrichShortVerificandoReply(
                                    content,
                                    SchedulingSlotCapture.peekSlotTimes(),
                                    SchedulingSlotCapture.peekRequestedDate().orElse(null),
                                    calendarZone);
                }
                if (content.isBlank() && !SchedulingSlotCapture.peekSlotTimes().isEmpty()) {
                    content =
                            "Segue a disponibilidade para "
                                    + SchedulingSlotCapture.peekRequestedDate()
                                            .map(d -> d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                            .orElse("a data pedida")
                                    + ".";
                }
                List<String> extraOutbound = List.of();
                Optional<AppointmentConfirmationDetails> confirmation = tools.takeSuccessfulAppointmentDetails();
                if (confirmation.isPresent()) {
                    AppointmentConfirmationDetails d = confirmation.get();
                    String card =
                            AppointmentConfirmationCardFormatter.formatConfirmationCard(
                                    d.serviceName(),
                                    d.clientDisplayName(),
                                    d.date(),
                                    d.timeHhMm(),
                                    appointmentConfirmationProperties.getLocationLine());
                    String base = content == null ? "" : content.strip();
                    content = base.isBlank() ? card : base + "\n\n" + card;
                    extraOutbound = mapsFollowUpMessages(appointmentConfirmationProperties.getMapsUrl());
                }
                if (content.isBlank()) {
                    throw new IllegalStateException("Resposta vazia do modelo de chat");
                }
                Optional<WhatsAppInteractiveReply> interactive =
                        SchedulingSlotCapture.takeWhatsAppInteractive(content, calendarZone);
                return new AICompletionResponse(content, interactive, extraOutbound);
            } finally {
                SchedulingSlotCapture.clear();
            }
        }

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(chatModelName).build();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));
        if (!CollectionUtils.isEmpty(request.conversationHistory())) {
            for (Message m : request.conversationHistory()) {
                messages.add(toSpringMessage(m));
            }
        }
        messages.add(new UserMessage(request.userMessage()));
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        AssistantMessage output = response.getResult().getOutput();
        String text = output.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Resposta vazia do modelo de chat");
        }
        return new AICompletionResponse(text.strip());
    }

    private ChatClientResponse invokeSchedulingChat(
            String systemContent, AICompletionRequest request, GeminiSchedulingTools tools) {
        GoogleGenAiChatOptions options =
                GoogleGenAiChatOptions.builder().model(chatModelName).internalToolExecutionEnabled(true).build();
        ChatClient.ChatClientRequestSpec spec =
                ChatClient.create(chatModel)
                        .prompt()
                        .system(systemContent)
                        .options(options)
                        .tools(tools);
        if (!CollectionUtils.isEmpty(request.conversationHistory())) {
            List<org.springframework.ai.chat.messages.Message> hist = new ArrayList<>();
            for (Message m : request.conversationHistory()) {
                hist.add(toSpringMessage(m));
            }
            spec = spec.messages(hist);
        }
        return spec.user(request.userMessage()).call().chatClientResponse();
    }

    /**
     * Ajusta o texto do assistente quando já há slots: não repete lista em texto (os botões substituem) e remove
     * perguntas redundantes sobre escolha de horário.
     */
    private static String enrichShortVerificandoReply(
            String content, List<String> slots, LocalDate requestedDate, ZoneId calendarZone) {
        slots = SchedulingSlotCapture.normalizeSlotTimes(slots);
        if (slots.isEmpty()) {
            return content == null ? "" : content.strip();
        }
        String c = content == null ? "" : content.strip();
        c = stripRedundantSchedulingPrompts(c);
        if (SchedulingSlotCapture.peekInteractiveMainText().isPresent()) {
            c = stripHorarioListEchoFromAssistant(c, slots);
            if (c.isBlank() || looksOnlyLikeVerifyingOrNoise(c)) {
                c = "Seguem os horários na mensagem seguinte (lista numerada).";
            }
            return c;
        }
        String choice = SchedulingSlotCapture.formatChoiceTitle(requestedDate, calendarZone);
        if (!choice.isEmpty() && !c.contains("Escolha um horário")) {
            c = choice + (c.isEmpty() ? "" : "\n\n" + c);
        }
        if (c.length() > 500) {
            return c;
        }
        boolean hasTimeInText = c.matches("(?s).*(\\d{1,2}:\\d{2}).*");
        if (hasTimeInText) {
            return c;
        }
        boolean soundsLikeChecking =
                c.length() < 120
                        && c.toLowerCase(Locale.ROOT).matches("(?s).*(verific|consult).*(dispon|disponibil|agend).*");
        if (soundsLikeChecking || (c.length() < 40 && !hasTimeInText)) {
            String block =
                    SchedulingSlotCapture.buildPremiumFormattedSlotList(
                            SchedulingSlotCapture.peekRequestedDate().orElse(null), calendarZone, slots);
            return block.isBlank() ? c : c + "\n\n" + block;
        }
        return c;
    }

    private static String stripRedundantSchedulingPrompts(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw;
        s =
                s.replaceAll(
                        "(?is)[\\s\\n]*[^.!?\\n]*\\bqual\\s+hor[áa]rio\\s+(?:o\\s+)?(?:senhor|sr\\.?|você|vc)\\s+"
                                + "(?:prefere|deseja|gostaria)[^.!?\\n]*[.!?]\\s*",
                        " ");
        s =
                s.replaceAll(
                        "(?is)[^.!?\\n]*\\binform(?:e|ar)\\s+(?:me\\s+)?qual\\s+hor[áa]rio[^.!?\\n]*[.!?]\\s*", " ");
        s = s.replaceAll("(?is)[^.!?\\n]*\\bme\\s+diga\\s+qual\\s+hor[áa]rio[^.!?\\n]*[.!?]\\s*", " ");
        return s.strip();
    }

    private static String stripHorarioListEchoFromAssistant(String content, List<String> slots) {
        String s = content;
        for (String slot : slots) {
            s = s.replace(slot, "");
        }
        s = s.replaceAll("(?m)\\b\\d{1,2}:\\d{2}\\s*[,;]\\s*", "");
        s = s.replaceAll("(?m)^\\s*[\\d\\s,:;]+$", "");
        s = s.replaceAll("[,;]{2,}", ", ");
        s = s.replaceAll("\\s*,\\s*,+", ", ");
        s = s.replaceAll("\\s{2,}", " ").strip();
        s = s.replaceAll("[,;\\s]+", " ").strip();
        s = s.replaceAll("^[,;\\s]+|[,;\\s]+$", "");
        if (s.matches("^[,;\\s]*$")) {
            return "";
        }
        return s;
    }

    private static boolean looksOnlyLikeVerifyingOrNoise(String c) {
        if (c == null || c.isBlank()) {
            return true;
        }
        String t = c.toLowerCase(Locale.ROOT);
        return t.length() < 8
                || (t.matches("(?s).*(verific|consult).*(dispon|disponibil|agend).*") && t.length() < 160);
    }

    private static String textFromChatResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String t = response.getResult().getOutput().getText();
        return t != null ? t.strip() : "";
    }

    private static List<String> mapsFollowUpMessages(String mapsUrl) {
        String m = AppointmentConfirmationCardFormatter.formatMapsFollowUp(mapsUrl);
        return m.isBlank() ? List.of() : List.of(m);
    }

    /**
     * Evita segunda chamada ao modelo em conversas gerais (custo/latência), mas cobre pedidos de marcação e
     * confirmações curtas típicas de WhatsApp.
     */
    static boolean likelySchedulingOrConfirmationTurn(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String t = userMessage.strip();
        if (t.length() <= 24 && SHORT_SCHEDULING_CONFIRM.matcher(t).matches()) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.contains("agend")
                || lower.contains("marcar")
                || lower.contains("marcação")
                || lower.contains("marcacao")
                || lower.contains("confirm")
                || lower.contains("horário")
                || lower.contains("horario")
                || lower.contains("dispon")
                || lower.contains("calend")
                || lower.contains("vaga")
                || t.contains("/")
                || t.chars().filter(Character::isDigit).count() >= 2;
    }

    private String buildSystemContent(AICompletionRequest request) {
        String schedulingAnchor = null;
        if (request.schedulingToolsEnabled()) {
            schedulingAnchor =
                    RagSystemPromptComposer.schedulingTemporalAnchor(ZoneId.of(calendarProperties.getZone()));
        }
        return RagSystemPromptComposer.compose(
                request.systemPrompt(),
                request.knowledgeHits(),
                !request.conversationHistory().isEmpty(),
                request.resumeAfterHumanIntervention(),
                request.schedulingToolsEnabled(),
                schedulingAnchor,
                request.crmContext());
    }

    private static org.springframework.ai.chat.messages.Message toSpringMessage(Message m) {
        if (m.senderType() == SenderType.HUMAN_ADMIN) {
            return new AssistantMessage("[Atendente humano] " + m.content());
        }
        return switch (m.role()) {
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> new AssistantMessage(m.content());
            case SYSTEM -> new SystemMessage(m.content());
        };
    }

    /**
     * Se o modelo respondeu só com «a verificar…» sem invocar {@code check_availability}, não há slots nem botões.
     * Obtém horários directamente do calendário quando a mensagem é claramente um pedido de disponibilidade.
     */
    private void maybeBackfillAvailabilityFromCalendar(AICompletionRequest request, ZoneId calendarZone) {
        if (!request.schedulingToolsEnabled()) {
            return;
        }
        if (!SchedulingSlotCapture.peekSlotTimes().isEmpty()) {
            return;
        }
        String msg = request.userMessage();
        if (msg == null || msg.isBlank()) {
            return;
        }
        String stripped = msg.strip();
        if (stripped.length() <= 28 && SHORT_SCHEDULING_CONFIRM.matcher(stripped).matches()) {
            return;
        }
        if (!isAvailabilityListingIntent(msg)
                && !isSchedulingAvailabilityFollowUpNudge(request)
                && !isConcreteDateInSchedulingFlow(request, calendarZone)) {
            return;
        }
        Optional<LocalDate> day = SchedulingAppointmentFallback.lastDateInTranscript(msg, calendarZone);
        if (day.isEmpty()) {
            day = SchedulingAppointmentFallback.lastDateInTranscript(mergeRecentTranscriptForDate(request), calendarZone);
        }
        if (day.isEmpty() && mentionsTomorrowLoose(msg)) {
            day = Optional.of(LocalDate.now(calendarZone).plusDays(1));
        }
        if (day.isEmpty()) {
            LOG.debug(
                    "Agendamento backfill: não foi possível inferir data (tenant={})",
                    request.tenantId().value());
            return;
        }
        LocalDate d = day.get();
        if (d.isBefore(LocalDate.now(calendarZone))) {
            return;
        }
        String iso = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            String result = appointmentSchedulingPort.checkAvailability(request.tenantId(), iso);
            List<String> times = SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(result);
            if (!times.isEmpty()) {
                String hintBlob = mergeRecentTranscriptForDate(request);
                String mainText = SchedulingSlotCapture.buildWhatsAppMainText(d, calendarZone, hintBlob);
                SchedulingSlotCapture.setStructuredAvailability(mainText, times, d);
                LOG.info(
                        "Agendamento: horários obtidos no servidor (modelo não invocou check_availability) tenant={} date={}",
                        request.tenantId().value(),
                        iso);
            }
        } catch (RuntimeException e) {
            LOG.warn(
                    "Agendamento backfill falhou tenant={} date={}: {}",
                    request.tenantId().value(),
                    iso,
                    e.toString());
        }
    }

    private static String mergeRecentTranscriptForDate(AICompletionRequest request) {
        var hist = request.conversationHistory();
        if (hist == null || hist.isEmpty()) {
            return request.userMessage();
        }
        int n = Math.min(8, hist.size());
        StringBuilder sb = new StringBuilder();
        for (int i = hist.size() - n; i < hist.size(); i++) {
            sb.append(hist.get(i).content()).append('\n');
        }
        sb.append(request.userMessage());
        return sb.toString();
    }

    /** Pedido de listagem de horários / disponibilidade (não confirmação curta). */
    private static boolean isAvailabilityListingIntent(String msg) {
        String u = msg.toLowerCase(Locale.ROOT);
        if (u.length() <= 15 && SHORT_SCHEDULING_CONFIRM.matcher(msg.strip()).matches()) {
            return false;
        }
        if (u.contains("horário")
                || u.contains("horario")
                || u.contains("horários")
                || u.contains("horarios")
                || u.contains("disponibilidade")
                || u.contains("disponibiliz")) {
            return true;
        }
        if (u.contains("quals") && (u.contains("horario") || u.contains("horário"))) {
            return true;
        }
        if (u.contains("vaga") && (u.contains("tem") || u.contains("há") || u.contains("ha "))) {
            return true;
        }
        return mentionsTomorrowLoose(msg)
                && (u.contains("qual") || u.contains("quais") || u.contains("dia") || u.contains("agenda"));
    }

    private static boolean mentionsTomorrowLoose(String msg) {
        String u = msg.toLowerCase(Locale.ROOT);
        return u.contains("amanh")
                || u.contains("ananha")
                || u.contains("manhã")
                || u.contains("manha");
    }

    /**
     * Segundo turno tipo «e aí?» depois de o assistente ter dito que ia verificar — não traz data na mensagem,
     * mas o assistente já referiu o dia no histórico.
     */
    private static boolean isSchedulingAvailabilityFollowUpNudge(AICompletionRequest request) {
        String msg = request.userMessage();
        if (msg == null) {
            return false;
        }
        String u = msg.strip().toLowerCase(Locale.ROOT);
        if (u.length() > 36) {
            return false;
        }
        boolean nudge =
                u.matches("e\\s+a[íi]\\??")
                        || "e ai?".equals(u)
                        || "e ai".equals(u)
                        || u.contains("cadê")
                        || u.contains("cade")
                        || "então?".equals(u)
                        || "e então?".equals(u)
                        || "e entao?".equals(u)
                        || u.contains("qual é")
                        || u.contains("qual e");
        if (!nudge) {
            return false;
        }
        String blob = mergeRecentTranscriptForDate(request);
        String lower = blob.toLowerCase(Locale.ROOT);
        return lower.contains("verific")
                || lower.contains("dispon")
                || lower.contains("agenda")
                || lower.contains("horário")
                || lower.contains("horario");
    }

    /**
     * Mensagem curta só com data (ex.: {@code 13/04}) depois de, no histórico, já se falar em agendar/serviço/horários —
     * caso típico em que o modelo responde «a verificar» sem invocar a ferramenta.
     */
    static boolean isConcreteDateInSchedulingFlow(AICompletionRequest request, ZoneId calendarZone) {
        String msg = request.userMessage();
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String stripped = msg.strip();
        if (stripped.length() > 36) {
            return false;
        }
        if (SchedulingAppointmentFallback.lastDateInTranscript(stripped, calendarZone).isEmpty()) {
            return false;
        }
        String blob = mergeRecentTranscriptForDate(request);
        return transcriptSuggestsActiveScheduling(blob.toLowerCase(Locale.ROOT));
    }

    private static boolean transcriptSuggestsActiveScheduling(String lower) {
        return lower.contains("agend")
                || lower.contains("marcar")
                || lower.contains("marcação")
                || lower.contains("marcacao")
                || lower.contains("horário")
                || lower.contains("horario")
                || lower.contains("dispon")
                || lower.contains("verific")
                || lower.contains("vaga")
                || lower.contains("alinhamento")
                || lower.contains("serviço")
                || lower.contains("servico")
                || lower.contains("data exata")
                || lower.contains("qual data")
                || lower.contains("para o dia")
                || lower.contains("confirmar a data")
                || lower.contains("confirme a data");
    }
}
