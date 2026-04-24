package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import java.text.Normalizer;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationCardFormatter;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.application.service.ChatService;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationDetails;
import com.atendimento.cerebro.application.scheduling.CancelOptionMap;
import com.atendimento.cerebro.application.scheduling.ReagendamentoDeParaHint;
import com.atendimento.cerebro.application.scheduling.SchedulingCancelSessionCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingCreateAppointmentResult;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.scheduling.SchedulingToolContext;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import com.atendimento.cerebro.infrastructure.config.CerebroAppointmentConfirmationProperties;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
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
                    + "horário concreto (ou confirmou um horário já listado) noutro turno. "
                    + "Cancelar: NÃO invoque check_availability. Use get_active_appointments; se vários itens, o cliente "
                    + "deve escolher; depois cancel_appointment com o ID mostrado antes do serviço na lista (ou «opção N» "
                    + "se o mapa da sessão usar esse formato).";

    /**
     * Prefixo injetado no system prompt quando o transcript sugere gestão/cancelamento — obriga a ferramenta antes de
     * texto solto.
     */
    private static final String SCHEDULING_CANCEL_SYSTEM_PREFIX =
            "[Gestão de agendamentos existentes] O utilizador deseja gerir agendamentos (cancelar, ver ou remover "
                    + "compromissos). Você DEVE invocar a ferramenta get_active_appointments para listar os compromissos "
                    + "AGENDADO deste contacto antes de qualquer outra ação de calendário. "
                    + "Não peça permissão para «mostrar a lista» ou «ver os agendamentos»: assim que o cliente mencionar "
                    + "cancelar/desmarcar (ou equivalente), apresente os agendamentos activos de imediato via "
                    + "get_active_appointments, sem perguntar se pode listar. "
                    + "Se o assistente perguntou se o cliente quer ver a lista e o cliente respondeu «sim», «sí», «ok» ou "
                    + "equivalente, invoque get_active_appointments imediatamente neste turno — não responda apenas com "
                    + "texto sem chamar a ferramenta. "
                    + "Não invoque check_availability nem create_appointment até a lista ou o cancelamento estarem tratados."
                    + "\n\n";

    /** Reforço quando o histórico sugere cancelar/excluir/remover — sem pedir disponibilidade ao calendário. */
    private static final String SCHEDULING_CANCEL_TOOL_RETRY_SUFFIX =
            "\n\n[Reforço do sistema] O utilizador ou o histórico indicam cancelar, excluir ou remover um agendamento. "
                    + "Invoque APENAS get_active_appointments (e depois cancel_appointment com o ID da lista ou "
                    + "«opção N»). NÃO invoque check_availability nem create_appointment neste turno. "
                    + "Se a mensagem actual for só confirmação («sim», «ok») para ver a lista, chame get_active_appointments "
                    + "neste turno, sem resposta só em texto.";

    /**
     * O Gemini às vezes devolve string vazia após executar ferramentas sem texto de follow-up, o que derrubava o
     * request com excepção. Preferimos resposta canónica em vez de falhar o circuito.
     */
    private static final String SCHEDULING_EMPTY_ASSISTANT_FALLBACK_PT =
            "Não recebi uma resposta do assistente neste momento. Pode repetir a sua pergunta ou, se estava a escolher "
                    + "um horário, responda com o número da opção.";

    private static final Pattern SHORT_SCHEDULING_CONFIRM =
            Pattern.compile("^(sim|sí|ok|confirmado|confirmo|pode|isso|perfeito|fechado)[.!\\s]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREETING_HEAD =
            Pattern.compile(
                    "^(oi|ola|bom dia|boa tarde|boa noite|hi|hello)([\\s!,.…?]|$)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Resposta típica a «quer que eu verifique os horários?» — deve disparar {@code check_availability} (reforço e
     * backfill), não fluxo de cancelamento nem silêncio por falta de data parseável.
     */
    private static final Pattern EXPLICIT_AVAILABILITY_CHECK_MESSAGE =
            Pattern.compile(
                    "^(?is)(?:verifica|verifique|verifiquem|confere|conferes|checa|cheque|chequem)(?:[.!…]+|\\s.*)?$");

    /** Resposta só com o ID numérico da lista de cancelamento (ex.: «2», «1234», «opção 2») — forçar {@code cancel_appointment} no servidor. */
    private static final Pattern BARE_CANCEL_OPTION_INDEX = Pattern.compile("^\\d{1,19}$");

    private static final Pattern BARE_CANCEL_OPCAO =
            Pattern.compile("^(?:op(ç|c)ão|opcao)\\s*\\d{1,19}$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CRM_CLIENT_NAME =
            Pattern.compile("Nome do cliente:\\s*([^\\.\\n]+)", Pattern.CASE_INSENSITIVE);
    /** Texto injectado por {@link ChatService#buildCrmContextForPrompt} — último serviço registado no CRM. */
    private static final Pattern CRM_LAST_SERVICE =
            Pattern.compile("servi[cç]o\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final GoogleGenAiChatModel chatModel;
    private final String chatModelName;
    private final AppointmentSchedulingPort appointmentSchedulingPort;
    private final AppointmentValidationService appointmentValidationService;
    private final AppointmentService appointmentService;
    private final CerebroGoogleCalendarProperties calendarProperties;

    private final CerebroAppointmentConfirmationProperties appointmentConfirmationProperties;

    public GeminiChatEngineAdapter(
            @Qualifier("googleGenAiChatModel") GoogleGenAiChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-1.5-flash}") String chatModelName,
            AppointmentSchedulingPort appointmentSchedulingPort,
            AppointmentValidationService appointmentValidationService,
            AppointmentService appointmentService,
            CerebroGoogleCalendarProperties calendarProperties,
            CerebroAppointmentConfirmationProperties appointmentConfirmationProperties) {
        this.chatModel = chatModel;
        this.chatModelName = chatModelName != null && !chatModelName.isBlank() ? chatModelName : DEFAULT_MODEL;
        this.appointmentSchedulingPort = appointmentSchedulingPort;
        this.appointmentValidationService = appointmentValidationService;
        this.appointmentService = appointmentService;
        this.calendarProperties = calendarProperties;
        this.appointmentConfirmationProperties = appointmentConfirmationProperties;
    }

    public AICompletionResponse complete(AICompletionRequest request) {
        String systemContent = buildSystemContent(request);

        if (request.schedulingToolsEnabled()) {
            ZoneId calendarZone = ZoneId.of(calendarProperties.getZone());
            boolean cancelContext = schedulingCancellationOrListManagementContext(request);
            String schedulingSystemContent =
                    cancelContext ? SCHEDULING_CANCEL_SYSTEM_PREFIX + systemContent : systemContent;
            try {
                SchedulingToolContext.resetContext();
                Optional<AICompletionResponse> directCancel =
                        tryDirectCancellationWithoutGemini(request, calendarZone);
                if (directCancel.isPresent()) {
                    return directCancel.get();
                }
                Optional<AICompletionResponse> directCreate =
                        tryDirectCreateAppointmentWithoutGemini(request, calendarZone);
                if (directCreate.isPresent()) {
                    return directCreate.get();
                }
                Optional<AICompletionResponse> directReschedule =
                        tryDirectRescheduleWithoutGemini(request, calendarZone);
                if (directReschedule.isPresent()) {
                    return directReschedule.get();
                }
                GeminiSchedulingTools tools =
                        new GeminiSchedulingTools(
                                request.tenantId(),
                                request.conversationId(),
                                appointmentSchedulingPort,
                                appointmentValidationService,
                                appointmentService,
                                calendarZone,
                                request.userMessage(),
                                mergeRecentTranscriptForDate(request),
                                request.schedulingEnforcedChoice(),
                                request.schedulingBlockCreateAppointment(),
                                request.schedulingSlotAnchorDate());
                String systemForInvoke = schedulingSystemContent;
                ChatClientResponse clientResponse = invokeSchedulingChat(systemForInvoke, request, tools);
                int afterFirst = tools.schedulingToolInvocationCount();
                // non-null após execução programática de get_active_appointments (valor pode ser vazio)
                String cancelFlowListingResult = null;
                boolean listMyAppointmentsIntent =
                        request.userMessage() != null
                                && SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent(
                                        request.userMessage());
                if (afterFirst == 0 && (cancelContext || listMyAppointmentsIntent)) {
                    LOG.warn(
                            "Gemini (agendamento): gestão/listagem sem ferramentas na 1ª tentativa (tenant={}); "
                                    + "cancelContext={} listAppointmentsIntent={}; "
                                    + "a executar get_active_appointments no servidor (sem segunda chamada ao modelo).",
                            request.tenantId().value(),
                            cancelContext,
                            listMyAppointmentsIntent);
                    cancelFlowListingResult = tools.get_active_appointments();
                } else if (afterFirst == 0
                        && shouldRetrySchedulingToolPass(request)
                        && !isShortPostBookingAckAfterCompletedBooking(
                                request.userMessage(), request.conversationHistory())) {
                    boolean cancelTranscript = schedulingCancellationOrListManagementContext(request);
                    String retrySuffix =
                            cancelTranscript ? SCHEDULING_CANCEL_TOOL_RETRY_SUFFIX : SCHEDULING_TOOL_RETRY_SUFFIX;
                    LOG.warn(
                            "Gemini (agendamento): nenhuma ferramenta executada (Java) na 1ª tentativa (tenant={}); "
                                    + "a repetir com reforço (cancelContext={}).",
                            request.tenantId().value(),
                            cancelTranscript);
                    clientResponse = invokeSchedulingChat(systemForInvoke + retrySuffix, request, tools);
                    if (tools.schedulingToolInvocationCount() == 0) {
                        LOG.warn(
                                "Gemini (agendamento): após reforço ainda sem execução de ferramentas (tenant={}). "
                                        + "O modelo não chamou check_availability/create_appointment/cancel_appointment; teste GEMINI_CHAT_MODEL=gemini-2.0-flash ou gemini-1.5-flash.",
                                request.tenantId().value());
                    }
                } else if (afterFirst == 0) {
                    LOG.debug(
                            "Gemini (agendamento): sem ferramentas neste turno (tenant={}); mensagem tratada como não agendamento.",
                            request.tenantId().value());
                }
                maybeBackfillAvailabilityFromCalendar(request, calendarZone);
                String content = textFromChatResponse(clientResponse.chatResponse());
                content =
                        applyProgrammaticCreateIfEnforcedChoiceIgnored(
                                request, tools, cancelContext, content);
                // Fallback se o modelo respondeu só em texto sem ferramentas (a via principal é tryDirectCancellationWithoutGemini).
                if (shouldForceProgrammaticCancelAppointment(request)
                        && tools.schedulingToolInvocationCount() == 0) {
                    LOG.info(
                            "Gemini (agendamento): fallback pós-modelo — cancel_appointment após lista (tenant={})",
                            request.tenantId().value());
                    String um = request.userMessage() != null ? request.userMessage().strip() : "";
                    String forced = tools.cancel_appointment("", um);
                    if (forced != null) {
                        if (forced.isEmpty()) {
                            content = "";
                        } else if (!forced.isBlank()) {
                            content = forced.strip();
                        }
                    }
                    if (AppointmentService.isSuccessfulCancellationReply(forced)) {
                        ChatService.clearCancellationContext(request.conversationId());
                        LOG.info(
                                "Gemini (agendamento): cancelamento concluído (fallback) — fluxo de cancelamento encerrado "
                                        + "(tenant={})",
                                request.tenantId().value());
                    }
                }
                if (cancelFlowListingResult != null) {
                    String listing = cancelFlowListingResult.strip();
                    if (!listing.isBlank()) {
                        content = listing;
                    }
                }
                String canonicalList = tools.peekLastGetActiveAppointmentsListText();
                if (canonicalList != null && !canonicalList.isBlank()) {
                    if (cancelContext) {
                        content = canonicalList.strip();
                    } else if (content == null || content.isBlank()) {
                        // Modelo frequentemente devolve texto vazio após get_active_appointments; injir o canónico.
                        content = canonicalList.strip();
                    }
                }
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
                Optional<String> whatsappTextOverride = Optional.empty();
                Optional<AppointmentConfirmationDetails> confirmation = tools.takeSuccessfulAppointmentDetails();
                if (confirmation.isPresent()) {
                    AppointmentConfirmationDetails d = confirmation.get();
                    String base =
                            AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(
                                    content == null ? "" : content.strip());
                    base = AppointmentConfirmationCardFormatter.stripEchoOfSchedulingCreateToolReturn(base);
                    if (appointmentConfirmationProperties.isWhatsappSuppressInChatWhenNotifying()) {
                        content =
                                base.isBlank()
                                        ? "Agendamento confirmado. O cliente recebeu a confirmação automática no WhatsApp."
                                        : base;
                        extraOutbound = mapsFollowUpMessages(appointmentConfirmationProperties.getMapsUrl());
                        whatsappTextOverride = Optional.of("");
                    } else {
                        String card =
                                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                                        d.appointmentDatabaseId(),
                                        d.serviceName(),
                                        d.clientDisplayName(),
                                        d.date(),
                                        d.timeHhMm(),
                                        appointmentConfirmationProperties.getLocationLine(),
                                        appointmentConfirmationProperties.getMapsUrl());
                        content = base.isBlank() ? card : base + "\n\n" + card;
                        extraOutbound = List.of();
                    }
                }
                if (content.isBlank()) {
                    if (cancelContext) {
                        if (cancelFlowListingResult == null) {
                            LOG.warn(
                                    "Gemini (agendamento): resposta vazia do modelo em contexto de cancelamento/gestão; "
                                            + "a obter lista via get_active_appointments (tenant={})",
                                    request.tenantId().value());
                            String listed = tools.get_active_appointments();
                            content = listed != null && !listed.isBlank() ? listed.strip() : "";
                        }
                        if (content.isBlank()) {
                            content = AppointmentService.CANCEL_LIST_UNAVAILABLE_FRIENDLY_MESSAGE;
                        }
                    } else {
                        if (listMyAppointmentsIntent) {
                            String listed = tools.get_active_appointments();
                            if (listed != null && !listed.isBlank()) {
                                content = listed.strip();
                            }
                        }
                        if (content.isBlank()) {
                            content = recoverEmptyNonCancelSchedulingContent(content, request, calendarZone);
                        }
                    }
                }
                content = ensureCancelOptionMapOnAssistantText(content);
                Optional<WhatsAppInteractiveReply> interactive =
                        SchedulingSlotCapture.takeWhatsAppInteractive(content, calendarZone);
                return new AICompletionResponse(content, interactive, extraOutbound, whatsappTextOverride);
            } finally {
                SchedulingToolContext.resetContext();
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
     * Evita excepção quando o texto final fica vazio fora de contexto de cancelamento (ex.: modelo sem mensagem após
     * tools). Reutiliza a lista de slots em sessão, se houver.
     */
    private static String recoverEmptyNonCancelSchedulingContent(
            String content, AICompletionRequest request, ZoneId calendarZone) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (!SchedulingSlotCapture.peekSlotTimes().isEmpty()) {
            String premium =
                    SchedulingSlotCapture.buildPremiumFormattedSlotList(
                            SchedulingSlotCapture.peekRequestedDate().orElse(null),
                            calendarZone,
                            SchedulingSlotCapture.peekSlotTimes());
            if (!premium.isBlank()) {
                return "Segue a disponibilidade para "
                        + SchedulingSlotCapture.peekRequestedDate()
                                .map(d -> d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                                .orElse("a data pedida")
                        + ".\n\n"
                        + premium;
            }
        }
        LOG.warn(
                "Gemini (agendamento): resposta vazia do modelo sem conteúdo recuperável (tenant={}).",
                request.tenantId().value());
        return SCHEDULING_EMPTY_ASSISTANT_FALLBACK_PT;
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

    /**
     * Garante que o texto persistido contém {@code [cancel_option_map:…]} quando a ferramenta preencheu o mapa na
     * sessão — o modelo por vezes parafraseia sem o apêndice e o número mostrado deixava de mapear para o ID na base.
     */
    private static String ensureCancelOptionMapOnAssistantText(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (content.contains(CancelOptionMap.APPENDIX_PREFIX)) {
            return content;
        }
        Map<Integer, Long> map = SchedulingCancelSessionCapture.getSelectionMap();
        if (map.isEmpty()) {
            return content;
        }
        return content.strip() + CancelOptionMap.buildAppendix(map);
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
     * Quando o backend já fixou data/hora ({@link AICompletionRequest#schedulingEnforcedChoice}) mas o modelo não
     * invocou {@code create_appointment} (resposta vazia ou só texto), cria o evento no servidor para não
     * apresentar «agendamento criado» sem persistência.
     */
    private static String applyProgrammaticCreateIfEnforcedChoiceIgnored(
            AICompletionRequest request,
            GeminiSchedulingTools tools,
            boolean cancelContext,
            String modelContent) {
        if (cancelContext
                || tools.createAppointmentWasInvoked()
                || request.schedulingBlockCreateAppointment()
                || request.schedulingEnforcedChoice().isEmpty()) {
            return modelContent;
        }
        SchedulingEnforcedChoice choice = request.schedulingEnforcedChoice().get();
        String iso = choice.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String time = choice.timeHhMm();
        String client = parseClientNameFromCrmContext(request.crmContext()).orElse("Cliente");
        String service = parseServiceNameFromCrmContext(request.crmContext()).orElse("Serviço");
        LOG.warn(
                "Gemini (agendamento): create_appointment no servidor — o modelo não invocou a ferramenta apesar de "
                        + "data/hora fixadas pelo backend (tenant={} date={} time={})",
                request.tenantId().value(),
                iso,
                time);
        String result = tools.create_appointment(iso, time, client, service);
        if (result != null && !result.isBlank()) {
            return result.strip();
        }
        return modelContent;
    }

    private static Optional<String> parseClientNameFromCrmContext(String crm) {
        if (crm == null || crm.isBlank()) {
            return Optional.empty();
        }
        Matcher m = CRM_CLIENT_NAME.matcher(crm);
        if (m.find()) {
            String s = m.group(1).strip();
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        }
        return Optional.empty();
    }

    private static Optional<String> parseServiceNameFromCrmContext(String crm) {
        if (crm == null || crm.isBlank()) {
            return Optional.empty();
        }
        Matcher m = CRM_LAST_SERVICE.matcher(crm);
        if (m.find()) {
            String s = m.group(1).strip();
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        }
        return Optional.empty();
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
        if (looksLikeExplicitAvailabilityCheckIntent(t)) {
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

    static boolean shouldRetrySchedulingToolPass(AICompletionRequest request) {
        if (request == null || request.userMessage() == null || request.userMessage().isBlank()) {
            return false;
        }
        String t = request.userMessage().strip();
        if (!(t.length() <= 24 && SHORT_SCHEDULING_CONFIRM.matcher(t).matches())) {
            return likelySchedulingOrConfirmationTurn(t);
        }
        return isShortConfirmationInSchedulingContext(request);
    }

    private static boolean isShortConfirmationInSchedulingContext(AICompletionRequest request) {
        if (lastAssistantIndicatesNoActiveAppointments(request)) {
            return false;
        }
        List<Message> h = request.conversationHistory();
        if (h == null || h.isEmpty()) {
            return false;
        }
        for (int i = h.size() - 1; i >= 0 && i >= h.size() - 8; i--) {
            Message m = h.get(i);
            if (m.content() == null) {
                continue;
            }
            String c = m.content().toLowerCase(Locale.ROOT);
            if (c.contains(SchedulingUserReplyNormalizer.SLOT_OPTIONS_APPENDIX_TOKEN.toLowerCase(Locale.ROOT))
                    || c.contains(SchedulingUserReplyNormalizer.SCHEDULING_DRAFT_APPENDIX_TOKEN.toLowerCase(Locale.ROOT))
                    || c.contains(CancelOptionMap.APPENDIX_PREFIX)
                    || c.contains("posso confirmar o agendamento")
                    || c.contains("agendamentos agendado")
                    || c.contains("check_availability")
                    || c.contains("create_appointment")
                    || c.contains("cancel_appointment")
                    || c.contains("horário")
                    || c.contains("horario")
                    || c.contains("disponibil")
                    || c.contains("agend")) {
                return true;
            }
        }
        return false;
    }

    /**
     * «ok» / «sim» após confirmação de agendamento no histórico — não forçar segunda chamada com reforço de
     * ferramentas.
     */
    static boolean isShortPostBookingAckAfterCompletedBooking(String userMessage, List<Message> history) {
        if (userMessage == null || history == null || history.isEmpty()) {
            return false;
        }
        String t = userMessage.strip();
        if (t.length() > 28 || !SHORT_SCHEDULING_CONFIRM.matcher(t).matches()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() == MessageRole.ASSISTANT
                    && m.senderType() == SenderType.BOT
                    && SchedulingUserReplyNormalizer.assistantMessageIndicatesCompletedScheduling(m.content())) {
                return true;
            }
        }
        return false;
    }

    private Optional<AICompletionResponse> tryDirectRescheduleWithoutGemini(
            AICompletionRequest request, ZoneId calendarZone) {
        if (request.conversationId() == null || request.conversationId().isBlank()) {
            return Optional.empty();
        }
        String um = request.userMessage() != null ? request.userMessage().strip() : "";
        if (!SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent(um)) {
            return Optional.empty();
        }
        Optional<ReagendamentoDeParaHint> hintOpt =
                SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(um, calendarZone);
        if (hintOpt.isEmpty()) {
            return Optional.empty();
        }
        ReagendamentoDeParaHint hint = hintOpt.get();
        Optional<Long> toCancel =
                appointmentService.resolveActiveAppointmentIdForReschedule(
                        request.tenantId(), request.conversationId(), calendarZone, um);
        if (toCancel.isEmpty()) {
            LOG.warn(
                    "Gemini (agendamento): reagendamento directo não resolveu appointmentId para cancelar "
                            + "(tenant={} conv={} msg={})",
                    request.tenantId().value(),
                    request.conversationId(),
                    um);
            return Optional.empty();
        }
        String iso = hint.day().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String availability = appointmentSchedulingPort.checkAvailability(request.tenantId(), iso);
        List<String> slots = SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(availability);
        String target = hm(hint.toTime());
        if (!slots.contains(target)) {
            String msg =
                    "O horário "
                            + target
                            + " não está disponível para "
                            + hint.day().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + ". O horário anterior "
                            + hm(hint.fromTime())
                            + " foi mantido. Escolha uma das opções disponíveis:";
            SchedulingSlotCapture.setStructuredAvailability(msg, slots, hint.day());
            Optional<WhatsAppInteractiveReply> interactive =
                    SchedulingSlotCapture.takeWhatsAppInteractive(msg, calendarZone);
            String rich = slots.isEmpty() ? msg : msg + "\n\n" + SchedulingSlotCapture.formatNumberedSlotLines(slots);
            return Optional.of(new AICompletionResponse(rich, interactive, List.of()));
        }
        String cancelResult =
                appointmentService.cancelAppointment(
                        request.tenantId(),
                        request.conversationId(),
                        "",
                        String.valueOf(toCancel.get()),
                        calendarZone);
        if (!AppointmentService.isSuccessfulCancellationReply(cancelResult)) {
            return Optional.of(new AICompletionResponse(cancelResult != null ? cancelResult.strip() : ""));
        }
        String client = parseClientNameFromCrmContext(request.crmContext()).orElse("Cliente");
        String service = parseServiceNameFromCrmContext(request.crmContext()).orElse("Serviço");
        String createResult =
                appointmentService.createAppointment(
                        request.tenantId(),
                        iso,
                        target,
                        client,
                        service,
                        request.conversationId(),
                        calendarZone);
        String result = createResult != null ? createResult.strip() : "";
        if (SchedulingCreateAppointmentResult.isSuccess(result)
                && appointmentConfirmationProperties.isWhatsappSuppressInChatWhenNotifying()) {
            String stored =
                    "Reagendamento concluído de "
                            + hm(hint.fromTime())
                            + " para "
                            + target
                            + " em "
                            + hint.day().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + ". O cliente recebeu a confirmação automática no WhatsApp.";
            return Optional.of(new AICompletionResponse(stored, Optional.empty(), List.of(), Optional.of("")));
        }
        return Optional.of(new AICompletionResponse(result));
    }

    private static String hm(LocalTime t) {
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    /**
     * «Verifica», «verifique a disponibilidade», etc. — pedido de consulta de horários, não de lista de cancelamentos.
     */
    private static boolean looksLikeExplicitAvailabilityCheckIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.strip();
        if (s.length() > 56) {
            return false;
        }
        if (SchedulingUserReplyNormalizer.looksLikeCancellationIntent(s)) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.contains("cancel") || lower.contains("desmarc") || lower.contains("anula")) {
            return false;
        }
        return EXPLICIT_AVAILABILITY_CHECK_MESSAGE.matcher(s).matches();
    }

    private String buildSystemContent(AICompletionRequest request) {
        ZoneId zone = ZoneId.of(calendarProperties.getZone());
        String schedulingBanner = null;
        String schedulingAnchor = null;
        if (request.schedulingToolsEnabled()) {
            schedulingBanner = RagSystemPromptComposer.schedulingTemporalAttentionBanner(zone);
            schedulingAnchor = RagSystemPromptComposer.schedulingTemporalAnchor(zone);
        }
        return RagSystemPromptComposer.compose(
                request.systemPrompt(),
                request.knowledgeHits(),
                !request.conversationHistory().isEmpty(),
                request.resumeAfterHumanIntervention(),
                request.schedulingToolsEnabled(),
                schedulingBanner,
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
        if (transcriptSuggestsCancellation(request)) {
            LOG.debug(
                    "Agendamento backfill omitido: histórico/mensagem sugerem cancelar/excluir/remover (tenant={})",
                    request.tenantId().value());
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

    /** Histórico completo + mensagem atual (para deteção de cancelamento e bloqueio de backfill de disponibilidade). */
    private static String mergeConversationTranscriptForTools(AICompletionRequest request) {
        StringBuilder sb = new StringBuilder();
        if (!CollectionUtils.isEmpty(request.conversationHistory())) {
            for (Message m : request.conversationHistory()) {
                sb.append(m.content()).append('\n');
            }
        }
        if (request.userMessage() != null) {
            sb.append(request.userMessage());
        }
        return sb.toString();
    }

    /**
     * Transcript para classificar intenção de cancelamento: ignora tudo antes da última confirmação de cancelamento
     * bem-sucedido, para não manter palavras antigas («cancelar») a forçar {@code cancelContext} após novo pedido.
     */
    private static String mergeConversationTranscriptForCancellationScoring(AICompletionRequest request) {
        List<Message> h = request.conversationHistory();
        if (h == null) {
            h = List.of();
        }
        int cut = SchedulingUserReplyNormalizer.indexOfLastAssistantSuccessfulCancellation(h);
        if (cut < 0) {
            return mergeConversationTranscriptForTools(request);
        }
        StringBuilder sb = new StringBuilder();
        for (int j = cut + 1; j < h.size(); j++) {
            sb.append(h.get(j).content()).append('\n');
        }
        if (request.userMessage() != null) {
            sb.append(request.userMessage());
        }
        return sb.toString();
    }

    /**
     * Histórico + mensagem atual sugerem cancelar/excluir/remover — bloqueia backfill {@code events.list} e escolhe o
     * sufixo de reforço adequado. Exposto ao pacote para testes.
     */
    static boolean transcriptSuggestsCancellation(AICompletionRequest request) {
        if (request.schedulingEnforcedChoice().isPresent()) {
            return false;
        }
        if (looksLikeGreetingMessage(request.userMessage())) {
            return false;
        }
        if (lastAssistantIndicatesNoActiveAppointments(request)
                && (request.userMessage() == null
                        || !SchedulingUserReplyNormalizer.looksLikeCancellationIntent(request.userMessage()))) {
            return false;
        }
        if (request.userMessage() != null
                && SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent(request.userMessage())) {
            return false;
        }
        if (request.userMessage() != null
                && SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent(request.userMessage())) {
            return false;
        }
        if (request.userMessage() != null && looksLikeExplicitAvailabilityCheckIntent(request.userMessage())) {
            return false;
        }
        return SchedulingUserReplyNormalizer.looksLikeCancellationInBlob(
                mergeConversationTranscriptForCancellationScoring(request));
    }

    private static boolean looksLikeGreetingMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized =
                Normalizer.normalize(raw.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return GREETING_HEAD.matcher(normalized).find();
    }

    private static boolean lastAssistantIndicatesNoActiveAppointments(AICompletionRequest request) {
        List<Message> h = request.conversationHistory();
        if (h == null || h.isEmpty()) {
            return false;
        }
        for (int i = h.size() - 1; i >= 0; i--) {
            Message m = h.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.content() == null) {
                continue;
            }
            String c = m.content().toLowerCase(Locale.ROOT);
            return c.contains("não há agendamentos com estado agendado")
                    || c.contains("nao ha agendamentos com estado agendado")
                    || c.contains("não há agendamentos ativos para cancelar")
                    || c.contains("nao ha agendamentos ativos para cancelar")
                    || c.contains("sem agendamentos ativos no momento")
                    || c.contains("não há agendamentos ativos no momento")
                    || c.contains("nao ha agendamentos ativos no momento")
                    || c.contains("não há agendamentos ativos")
                    || c.contains("nao ha agendamentos ativos");
        }
        return false;
    }

    /**
     * O modelo devolveu só texto sem ferramentas, mas o cliente enviou o ID da lista de cancelamento (só dígitos ou
     * «opção N») e o último assistente mostrou a lista — o adaptador deve invocar {@code cancel_appointment} no servidor.
     */
    static boolean shouldForceProgrammaticCancelAppointment(AICompletionRequest request) {
        if (request == null || request.userMessage() == null) {
            return false;
        }
        String um = request.userMessage().strip();
        if (um.isEmpty()) {
            return false;
        }
        if (!BARE_CANCEL_OPTION_INDEX.matcher(um).matches() && !BARE_CANCEL_OPCAO.matcher(um).matches()) {
            return false;
        }
        return SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(request.conversationHistory());
    }

    /**
     * Cliente escolheu só o ID numérico ou «opção N» após a lista de cancelamento — resolve o ID, chama
     * {@link AppointmentService#cancelAppointment} directamente (sem Gemini). A mensagem devolvida só é de sucesso depois
     * de {@code deleteCalendarEvent} e gravação na base em {@code cancelAppointment}.
     */
    private Optional<AICompletionResponse> tryDirectCancellationWithoutGemini(
            AICompletionRequest request, ZoneId calendarZone) {
        if (!shouldForceProgrammaticCancelAppointment(request)) {
            return Optional.empty();
        }
        String convId = request.conversationId();
        if (convId == null || convId.isBlank()) {
            return Optional.empty();
        }
        String um = request.userMessage() != null ? request.userMessage().strip() : "";
        String blob = mergeConversationTranscriptForTools(request);
        String resolved = CancelOptionMap.resolveAppointmentIdForCancel(um, blob);
        LOG.info(
                "Gemini (agendamento): intercepção de ID da lista — cancelamento directo sem chamada ao modelo (tenant={} "
                        + "entrada={} appointmentIdResolvido={})",
                request.tenantId().value(),
                um,
                resolved);
        String outcome =
                appointmentService.cancelAppointment(request.tenantId(), convId, "", resolved, calendarZone);
        ChatService.clearCancellationContext(convId);
        if (AppointmentService.isSuccessfulCancellationReply(outcome)) {
            LOG.info(
                    "Gemini (agendamento): cancelamento concluído no calendário e na base — fluxo de cancelamento "
                            + "encerrado (tenant={})",
                    request.tenantId().value());
        }
        return Optional.of(new AICompletionResponse(outcome != null ? outcome.strip() : ""));
    }

    /**
     * Quando o {@link ChatService} já expandiu «sim» para a instrução de confirmação com data/hora fixadas, cria o
     * compromisso sem chamar o Gemini — evita respostas só em texto, confusão com cancelamento e falta de persistência.
     */
    private Optional<AICompletionResponse> tryDirectCreateAppointmentWithoutGemini(
            AICompletionRequest request, ZoneId calendarZone) {
        if (!SchedulingUserReplyNormalizer.isBackendCreateConfirmationInstruction(request.userMessage())) {
            return Optional.empty();
        }
        if (request.schedulingEnforcedChoice().isEmpty() || request.schedulingBlockCreateAppointment()) {
            return Optional.empty();
        }
        LOG.info(
                "Gemini (agendamento): confirmação com data/hora fixadas — create_appointment directo sem modelo (tenant={})",
                request.tenantId().value());
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        request.tenantId(),
                        request.conversationId(),
                        appointmentSchedulingPort,
                        appointmentValidationService,
                        appointmentService,
                        calendarZone,
                        request.userMessage(),
                        mergeRecentTranscriptForDate(request),
                        request.schedulingEnforcedChoice(),
                        request.schedulingBlockCreateAppointment(),
                        request.schedulingSlotAnchorDate());
        SchedulingEnforcedChoice choice = request.schedulingEnforcedChoice().get();
        String iso = choice.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String time = choice.timeHhMm();
        String client = parseClientNameFromCrmContext(request.crmContext()).orElse("Cliente");
        String service = parseServiceNameFromCrmContext(request.crmContext()).orElse("Serviço");
        String result = tools.create_appointment(iso, time, client, service);
        String content = result != null ? result.strip() : "";
        List<String> extraOutbound = List.of();
        Optional<String> whatsappTextOverride = Optional.empty();
        Optional<AppointmentConfirmationDetails> confirmation = tools.takeSuccessfulAppointmentDetails();
        if (confirmation.isPresent()) {
            AppointmentConfirmationDetails d = confirmation.get();
            String base =
                    AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(
                            content == null ? "" : content.strip());
            base = AppointmentConfirmationCardFormatter.stripEchoOfSchedulingCreateToolReturn(base);
            if (appointmentConfirmationProperties.isWhatsappSuppressInChatWhenNotifying()) {
                content =
                        base.isBlank()
                                ? "Agendamento confirmado. O cliente recebeu a confirmação automática no WhatsApp."
                                : base;
                extraOutbound = mapsFollowUpMessages(appointmentConfirmationProperties.getMapsUrl());
                whatsappTextOverride = Optional.of("");
            } else {
                String card =
                        AppointmentConfirmationCardFormatter.formatConfirmationCard(
                                d.appointmentDatabaseId(),
                                d.serviceName(),
                                d.clientDisplayName(),
                                d.date(),
                                d.timeHhMm(),
                                appointmentConfirmationProperties.getLocationLine(),
                                appointmentConfirmationProperties.getMapsUrl());
                content = base.isBlank() ? card : base + "\n\n" + card;
                extraOutbound = List.of();
            }
        }
        if (content.isBlank()) {
            throw new IllegalStateException("Resposta vazia após create_appointment directo");
        }
        content = ensureCancelOptionMapOnAssistantText(content);
        Optional<WhatsAppInteractiveReply> interactive =
                SchedulingSlotCapture.takeWhatsAppInteractive(content, calendarZone);
        return Optional.of(new AICompletionResponse(content, interactive, extraOutbound, whatsappTextOverride));
    }

    /**
     * Contexto em que o fluxo é gerir/cancelar agendamentos existentes: palavras de cancelamento no transcript ou
     * confirmação curta («sim») depois do assistente ter pedido lista / falado em cancelar ou agendamentos AGENDADO.
     */
    static boolean schedulingCancellationOrListManagementContext(AICompletionRequest request) {
        if (request.schedulingEnforcedChoice().isPresent()) {
            // Confirmação com data/hora fixadas pelo backend (ex.: «sim» após «Posso confirmar o agendamento?»).
            // Não tratar como fluxo de cancelamento — senão get_active_appointments substitui a resposta e o evento não é criado.
            return false;
        }
        if (request.userMessage() != null
                && SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent(request.userMessage())) {
            return false;
        }
        if (transcriptSuggestsCancellation(request)) {
            return true;
        }
        return shortConfirmAfterAssistantListOrCancelPrompt(request);
    }

    /**
     * «Ver a lista» na mensagem do assistente refere-se a agendamentos/compromissos — não a «ver a lista de
     * serviços» do pitch comercial (evita que «sim» dispare get_active_appointments).
     */
    private static boolean assistantPromptVerAListaTargetsAppointments(String cLower) {
        if (!cLower.contains("ver a lista")) {
            return false;
        }
        if (Pattern.compile("lista\\s+de\\s+servi").matcher(cLower).find()) {
            return false;
        }
        if ((cLower.contains("serviço")
                        || cLower.contains("servico")
                        || cLower.contains("servicos"))
                && !(cLower.contains("agend")
                        || cLower.contains("compromis")
                        || cLower.contains("cancel")
                        || cLower.contains("desmarc")
                        || cLower.contains("marcad"))) {
            return false;
        }
        return true;
    }

    private static boolean shortConfirmAfterAssistantListOrCancelPrompt(AICompletionRequest request) {
        if (lastAssistantIndicatesNoActiveAppointments(request)) {
            return false;
        }
        String um = request.userMessage();
        if (um == null || um.isBlank()) {
            return false;
        }
        String stripped = um.strip();
        if (BARE_CANCEL_OPTION_INDEX.matcher(stripped).matches()
                || BARE_CANCEL_OPCAO.matcher(stripped).matches()) {
            if (SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(
                    request.conversationHistory())) {
                return true;
            }
        }
        if (stripped.length() > 24 || !SHORT_SCHEDULING_CONFIRM.matcher(stripped).matches()) {
            return false;
        }
        List<Message> h = request.conversationHistory();
        if (h == null || h.isEmpty()) {
            return false;
        }
        for (int i = h.size() - 1; i >= 0; i--) {
            Message m = h.get(i);
            if (m.role() != MessageRole.ASSISTANT) {
                continue;
            }
            String raw = m.content();
            if (raw != null && raw.contains(SchedulingUserReplyNormalizer.SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
                return false;
            }
            if (raw == null) {
                continue;
            }
            String c = raw.toLowerCase(Locale.ROOT);
            if (c.contains("posso confirmar") && c.contains("agendamento")) {
                return false;
            }
            return c.contains("cancelar")
                    || c.contains("cancelamento")
                    || c.contains("desmarcar")
                    || c.contains("agendamentos agendado")
                    || c.contains("cancel_option_map")
                    || c.contains("appointmentid=")
                    || assistantPromptVerAListaTargetsAppointments(c)
                    || c.contains("lista de agendamento")
                    || (c.contains("listar") && (c.contains("agendamento") || c.contains("compromisso")))
                    || c.contains("qual deseja cancelar")
                    || c.contains("quer cancelar");
        }
        return false;
    }

    /** Pedido de listagem de horários / disponibilidade (não confirmação curta). */
    private static boolean isAvailabilityListingIntent(String msg) {
        if (looksLikeExplicitAvailabilityCheckIntent(msg)) {
            return true;
        }
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
