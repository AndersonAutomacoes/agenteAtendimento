package com.atendimento.cerebro.application.service;



import com.atendimento.cerebro.application.ai.AiChatProvider;

import com.atendimento.cerebro.application.dto.AICompletionRequest;

import com.atendimento.cerebro.application.dto.ChatCommand;

import com.atendimento.cerebro.application.dto.ChatResult;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;

import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;

import com.atendimento.cerebro.application.port.in.ChatUseCase;

import com.atendimento.cerebro.application.port.out.AIEnginePort;

import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;

import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;

import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;

import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;

import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;

import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;

import com.atendimento.cerebro.domain.conversation.ConversationContext;

import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;

import com.atendimento.cerebro.domain.tenant.TenantConfiguration;

import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.scheduling.SlotChoiceExpansion;
import com.atendimento.cerebro.application.scheduling.SchedulingExplicitTimeShortcut;
import com.atendimento.cerebro.application.scheduling.SchedulingRescheduleIdExtractor;
import com.atendimento.cerebro.application.scheduling.SchedulingToolContext;
import com.atendimento.cerebro.application.scheduling.SystemPromptPlaceholders;
import com.atendimento.cerebro.application.scheduling.SchedulingCreateAppointmentResult;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingServiceAttribution;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.application.scheduling.CancelOptionMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.List;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



/**

 * Ordem do fluxo: (1) contexto, (2) RAG com embeddings Google GenAI ({@code KnowledgeBasePort}),

 * (3) geração da resposta com o motor escolhido em {@link com.atendimento.cerebro.application.dto.ChatCommand#chatProvider()}.

 */

public class ChatService implements ChatUseCase {
    private static final String AXEZAP_INTRODUCTION_RULE =
            "Regra de apresentação: ao iniciar atendimento com um cliente, apresente-se como: "
                    + "'Olá! Sou o assistente inteligente da [Empresa], operando através da tecnologia AxeZap.'";

    private static final Pattern GREETING_HEAD =
            Pattern.compile(
                    "^(oi|ola|bom dia|boa tarde|boa noite|hi|hello)([\\s!,.…?]|$)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_CONFIRMATION =
            Pattern.compile(
                    "^(sim|sí|confirmo|confirmado|pode|ok|isso|perfeito|fechado|pode\\s+ser|pode\\s+confirmar)\\b[.!\\s]*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CLOCK_TIME = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern CLOCK_TIME_ONLY = Pattern.compile("^\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\s*$");

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

    /**
     * O domínio rejeita {@link Message} com conteúdo em branco. Em fluxos que não ecoam texto no chat (ex. cancelamento
     * com confirmação assíncrona no WhatsApp), ainda assim o turno de assistente é guardado com este carácter invisível
     * (U+200C) para o histórico; o {@link ChatResult} continua a devolver o outbound vazio.
     */
    private static final String ASSISTANT_HISTORY_PLACEHOLDER_WHEN_NO_VISIBLE_TEXT = "\u200C";

    private static String forAssistantMessageRecord(String content) {
        if (content == null || content.isBlank()) {
            return ASSISTANT_HISTORY_PLACEHOLDER_WHEN_NO_VISIBLE_TEXT;
        }
        return content;
    }

    /** Últimas mensagens em {@code conversation_message} enviadas ao modelo (incl. HUMAN_ADMIN). */

    private static final int CONVERSATION_HISTORY_MAX_MESSAGES = 15;



    private final ConversationContextStorePort conversationContextStore;

    private final KnowledgeBasePort knowledgeBase;

    private final AIEnginePort aiEngine;

    private final TenantConfigurationStorePort tenantConfigurationStore;

    private final ConversationBotStatePort conversationBotStatePort;

    private final CrmCustomerStorePort crmCustomerStore;

    private final CrmCustomerQueryPort crmCustomerQuery;

    private final TenantAppointmentQueryPort tenantAppointmentQuery;

    private final AppointmentSchedulingPort appointmentScheduling;

    private final AppointmentService appointmentService;

    private final String schedulingZoneId;

    private final boolean whatsappSuppressInChatWhenNotifying;



    public ChatService(

            ConversationContextStorePort conversationContextStore,

            KnowledgeBasePort knowledgeBase,

            AIEnginePort aiEngine,

            TenantConfigurationStorePort tenantConfigurationStore,

            ConversationBotStatePort conversationBotStatePort,

            CrmCustomerStorePort crmCustomerStore,

            CrmCustomerQueryPort crmCustomerQuery,

            TenantAppointmentQueryPort tenantAppointmentQuery,

            AppointmentSchedulingPort appointmentScheduling,

            AppointmentService appointmentService,

            String schedulingZoneId,
            boolean whatsappSuppressInChatWhenNotifying) {

        this.conversationContextStore = conversationContextStore;

        this.knowledgeBase = knowledgeBase;

        this.aiEngine = aiEngine;

        this.tenantConfigurationStore = tenantConfigurationStore;

        this.conversationBotStatePort = conversationBotStatePort;

        this.crmCustomerStore = crmCustomerStore;

        this.crmCustomerQuery = crmCustomerQuery;

        this.tenantAppointmentQuery = tenantAppointmentQuery;

        this.appointmentScheduling = appointmentScheduling;

        this.appointmentService = appointmentService;

        this.schedulingZoneId = schedulingZoneId != null && !schedulingZoneId.isBlank()

                ? schedulingZoneId.strip()

                : "America/Bahia";

        this.whatsappSuppressInChatWhenNotifying = whatsappSuppressInChatWhenNotifying;

    }



    @Override

    public ChatResult chat(ChatCommand command) {

        var tenantId = command.tenantId();

        var conversationId = command.conversationId();

        var userText = command.userMessage();

        if (isGreetingMessage(userText)) {
            forceResetContext();
            LOG.info("[ContextReset] reason=greeting | conversationId={}", conversationId.value());
        }

        crmCustomerStore.ensureOnConversationStart(tenantId, conversationId.value(), Optional.empty());



        ConversationContext context = conversationContextStore

                .load(tenantId, conversationId)

                .orElseGet(() -> ConversationContext.builder()

                        .tenantId(tenantId)

                        .conversationId(conversationId)

                        .build());



        AiChatProvider provider =

                command.chatProvider() != null ? command.chatProvider() : AiChatProvider.GEMINI;



        Optional<TenantConfiguration> tenantConfig = tenantConfigurationStore.findByTenantId(tenantId);

        String systemPrompt =
                SystemPromptPlaceholders.apply(
                        tenantConfig.map(tc -> tc.systemPrompt().strip()).orElse(""),
                        ZoneId.of(schedulingZoneId));
        systemPrompt =
                (systemPrompt == null || systemPrompt.isBlank())
                        ? AXEZAP_INTRODUCTION_RULE
                        : AXEZAP_INTRODUCTION_RULE + "\n\n" + systemPrompt;

        if (tenantConfig.isEmpty()) {

            LOG.warn(

                    "Sem registo em tenant_configuration para tenantId={}; a persona (system prompt) não será aplicada — alinhe o id com o usado no dashboard e grave a configuração.",

                    tenantId.value());

        } else if (systemPrompt.isEmpty()) {

            LOG.warn(

                    "tenant_configuration.system_prompt está vazio para tenantId={}; defina a personalidade via API de settings ou na base de dados.",

                    tenantId.value());

        }



        List<KnowledgeHit> knowledgeHits = knowledgeBase.findTopThreeRelevantFragments(tenantId, userText);



        Message userMessage = Message.userMessage(userText);



        List<Message> fromStore = takeLast(context.getMessages(), CONVERSATION_HISTORY_MAX_MESSAGES);

        List<Message> historyForAi = new ArrayList<>(fromStore);

        if (historyForAi.isEmpty() && !command.whatsAppHistoryPriorTurns().isEmpty()) {

            historyForAi = new ArrayList<>(takeLast(command.whatsAppHistoryPriorTurns(), CONVERSATION_HISTORY_MAX_MESSAGES));

        }



        boolean resumeAfterHuman =

                conversationId.waDigitsIfPresent()

                        .map(

                                digits ->

                                        conversationBotStatePort.consumeResumeAiContextIfPending(

                                                tenantId, digits))

                        .orElse(false);



        // Ferramentas de agendamento só existem no motor Gemini. Devem estar disponíveis mesmo sem

        // google_calendar_id no tenant: em modo mock o backend grava em tenant_appointments com ID local;

        // com Google real, create_appointment devolve erro explícito até o tenant configurar o calendário.

        boolean schedulingTools = provider == AiChatProvider.GEMINI;

        Optional<ChatResult> numericChoiceSanityCheck =
                maybeHandleLikelyMisheardNumericChoice(
                        schedulingTools, userText, historyForAi, context, userMessage);
        if (numericChoiceSanityCheck.isPresent()) {
            return numericChoiceSanityCheck.get();
        }

        boolean cancelIntent = SchedulingUserReplyNormalizer.looksLikeCancellationIntent(userText);
        if (cancelIntent) {
            historyForAi = SchedulingUserReplyNormalizer.stripSchedulingStateFromHistory(historyForAi);
            LOG.info(
                    "[scheduling-cancel] Rascunho de agendamento (slot_options/slot_date/scheduling_draft) removido do histórico — intenção cancelar.");
        }

        boolean rescheduleIntent =
                schedulingTools && SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent(userText);

        boolean schedulingRestartIntent =
                schedulingTools
                        && SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent(userText)
                        && !cancelIntent
                        && !rescheduleIntent;
        if (schedulingRestartIntent) {
            clearCancellationContext(conversationId.value());
            historyForAi = SchedulingUserReplyNormalizer.stripInternalAppendicesFromHistory(historyForAi);
            LOG.info(
                    "[scheduling-restart] Apêndices internos (slots e cancel_option_map) removidos do histórico — intenção marcar horário após fluxo de cancelamento.");
        }

        if (rescheduleIntent) {
            historyForAi = SchedulingUserReplyNormalizer.stripSchedulingStateFromHistory(historyForAi);
            systemPrompt =
                    systemPrompt == null || systemPrompt.isEmpty()
                            ? SchedulingUserReplyNormalizer.RESCHEDULE_SYSTEM_BLOCK
                            : systemPrompt + "\n\n" + SchedulingUserReplyNormalizer.RESCHEDULE_SYSTEM_BLOCK;
            LOG.info(
                    "[scheduling-reschedule] Histórico sem rascunho de slots; systemPrompt com RESCHEDULE_SYSTEM_BLOCK.");
        }

        final List<Message> historyForSlotExpansion = historyForAi;

        Optional<String> selectedServiceByIndex =
                schedulingTools && !cancelIntent && !rescheduleIntent
                        && SchedulingUserReplyNormalizer
                                .shouldInterpretNumericChoiceAsServiceSelection(historyForSlotExpansion)
                        ? SchedulingUserReplyNormalizer.resolveSelectedServiceFromUserChoice(
                                userText, historyForSlotExpansion)
                        : Optional.empty();

        SlotChoiceExpansion slotExpansion =
                schedulingTools && !cancelIntent && !rescheduleIntent
                        ? (selectedServiceByIndex.isPresent()
                                ? SlotChoiceExpansion.unchanged(userText)
                                : SchedulingExplicitTimeShortcut.tryExpand(
                                                tenantId,
                                                userText,
                                                historyForSlotExpansion,
                                                ZoneId.of(schedulingZoneId),
                                                appointmentScheduling,
                                                appointmentService)
                                        .orElseGet(
                                                () ->
                                                        SchedulingUserReplyNormalizer.expandNumericSlotChoice(
                                                                userText, historyForSlotExpansion)))
                        : SlotChoiceExpansion.unchanged(userText);
        String userMessageForAi = slotExpansion.expandedUserMessage();

        Optional<LocalDate> schedulingSlotAnchorDate =
                schedulingTools
                        ? SchedulingUserReplyNormalizer.parseLastSlotDateFromHistory(historyForAi)
                        : Optional.empty();



        Optional<CrmCustomerRecord> crmRow =
                crmCustomerQuery.findByTenantAndConversationId(tenantId, conversationId.value());
        Optional<TenantAppointmentListItem> lastAppointment =
                tenantAppointmentQuery.findMostRecentByConversationId(
                        tenantId, conversationId.value(), schedulingZoneId);
        Optional<ChatResult> rescheduleConfirmationDraft =
                maybePrepareRescheduleDraftForExplicitTime(
                        schedulingTools,
                        tenantId,
                        conversationId.value(),
                        userText,
                        context,
                        userMessage,
                        historyForSlotExpansion,
                        ZoneId.of(schedulingZoneId));
        if (rescheduleConfirmationDraft.isPresent()) {
            return rescheduleConfirmationDraft.get();
        }
        Optional<ChatResult> rescheduleList =
                maybeListActiveAppointmentsForRescheduleContext(
                        schedulingTools,
                        rescheduleIntent,
                        tenantId,
                        conversationId.value(),
                        userText,
                        historyForSlotExpansion,
                        context,
                        userMessage,
                        ZoneId.of(schedulingZoneId));
        if (rescheduleList.isPresent()) {
            return rescheduleList.get();
        }
        Optional<ChatResult> continueAfterPastTime =
                maybeContinueSchedulingAfterPastTimeRejection(
                        schedulingTools,
                        tenantId,
                        userText,
                        historyForSlotExpansion,
                        context,
                        userMessage,
                        ZoneId.of(schedulingZoneId));
        if (continueAfterPastTime.isPresent()) {
            return continueAfterPastTime.get();
        }
        Optional<ChatResult> rescheduleAskTimeById =
                maybeAskRescheduleDateTimeForExplicitAppointmentId(
                        schedulingTools,
                        tenantId,
                        conversationId.value(),
                        userText,
                        context,
                        userMessage,
                        historyForSlotExpansion,
                        ZoneId.of(schedulingZoneId));
        if (rescheduleAskTimeById.isPresent()) {
            return rescheduleAskTimeById.get();
        }
        Optional<ChatResult> directRescheduleById =
                maybeExecuteDirectRescheduleWithExplicitAppointmentId(
                        schedulingTools,
                        tenantId,
                        conversationId.value(),
                        userText,
                        context,
                        userMessage,
                        crmRow,
                        ZoneId.of(schedulingZoneId));
        if (directRescheduleById.isPresent()) {
            return directRescheduleById.get();
        }

        String crmContext =
                buildCrmContextForPrompt(
                        conversationId.value(),
                        crmRow,
                        lastAppointment,
                        schedulingZoneId);

        // --- Bypass total do modelo quando a confirmação é determinística ---
        if (slotExpansion.hardcodedAssistantReply().isPresent()) {
            LOG.info("[scheduling-bypass] Resposta hardcoded para opção {} — IA não chamada",
                    slotExpansion.optionNumber());
            String hardcoded = slotExpansion.hardcodedAssistantReply().get();
            String stored = hardcoded;
            if (slotExpansion.pendingConfirmationDraft().isPresent()) {
                stored = SchedulingUserReplyNormalizer.appendSchedulingDraft(
                        hardcoded, slotExpansion.pendingConfirmationDraft().get());
            }
            stored =
                    reattachSelectedServiceToSlotConfirmMessageIfMissing(
                            stored, historyForSlotExpansion);
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
            ConversationContext updated = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updated);
            return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored));
        }

        if (selectedServiceByIndex.isPresent()) {
            String service = selectedServiceByIndex.get().strip();
            clearCancellationContext(conversationId.value());
            Optional<SchedulingEnforcedChoice> draftOrRecovered =
                    SchedulingUserReplyNormalizer.parseLastDraftFromHistory(historyForSlotExpansion)
                            .or(
                                    () ->
                                            SchedulingExplicitTimeShortcut.recoverEnforcedChoiceFromUserHistory(
                                                    historyForSlotExpansion, ZoneId.of(schedulingZoneId)));
            String dayPt;
            if (draftOrRecovered.isPresent()) {
                SchedulingEnforcedChoice d = draftOrRecovered.get();
                dayPt = d.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.forLanguageTag("pt-BR")));
                String hardcoded =
                        "Perfeito! O serviço *"
                                + service
                                + "* para *"
                                + dayPt
                                + "* às *"
                                + d.timeHhMm()
                                + "*. Posso confirmar o agendamento?";
                String stored =
                        SchedulingUserReplyNormalizer.appendSelectedService(
                                SchedulingUserReplyNormalizer.appendSchedulingDraft(hardcoded, d), service);
                Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
                ConversationContext updated = context.append(userMessage, assistantMessage);
                conversationContextStore.save(updated);
                return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored));
            }
            String hardcoded =
                    "Perfeito! Você escolheu *"
                            + service
                            + "*.\n\nAgora me diga a data desejada para o agendamento.";
            String stored = SchedulingUserReplyNormalizer.appendSelectedService(hardcoded, service);
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
            ConversationContext updated = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updated);
            return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored));
        }

        Optional<SchedulingEnforcedChoice> effectiveChoiceForCreate =
                slotExpansion.enforcedChoice()
                        .or(
                                () ->
                                        maybeRecoverEnforcedChoiceFromHistoryOnConfirmation(
                                                schedulingTools,
                                                userText,
                                                historyForSlotExpansion,
                                                ZoneId.of(schedulingZoneId)));
        boolean confirmedCreateTurn =
                SchedulingUserReplyNormalizer.isBackendCreateConfirmationInstruction(userMessageForAi)
                        || isShortConfirmationMessage(userText);
        if (schedulingTools
                && effectiveChoiceForCreate.isPresent()
                && !slotExpansion.blockCreateAppointmentThisTurn()
                && confirmedCreateTurn) {
            SchedulingEnforcedChoice choice = effectiveChoiceForCreate.get();
            String iso = choice.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
            ZoneId calendarZone = ZoneId.of(schedulingZoneId);
            String clientName =
                    crmRow
                            .map(CrmCustomerRecord::fullName)
                            .filter(n -> n != null && !n.isBlank())
                            .map(String::strip)
                            .orElse("Cliente");
            Optional<String> recentRescheduleRequest =
                    findRecentRescheduleRequestFromHistory(historyForAi, calendarZone);
            boolean recentRescheduleIntentInHistory =
                    SchedulingUserReplyNormalizer.hasRecentRescheduleUserIntentInHistory(historyForAi, 12);
            Optional<String> serviceNameOpt =
                    SchedulingExplicitTimeShortcut.parseServiceNameForCreateFromHistory(historyForAi);
            if (serviceNameOpt.isEmpty()) {
                String recentUserText =
                        SchedulingServiceAttribution.mergeRecentUserText(
                                historyForAi, userText != null ? userText : "", 20);
                serviceNameOpt =
                        appointmentService.resolveCatalogServiceMentionFromText(
                                tenantId, recentUserText);
            }
            Optional<Long> toCancelId = Optional.empty();
            if (recentRescheduleRequest.isPresent()) {
                toCancelId =
                        appointmentService.resolveActiveAppointmentIdForReschedule(
                                tenantId, conversationId.value(), calendarZone, recentRescheduleRequest.get());
            }
            if (toCancelId.isEmpty()) {
                toCancelId =
                        findRecentRescheduleAppointmentIdFromHistory(
                                historyForAi, calendarZone, userText);
            }
            if (toCancelId.isEmpty() && recentRescheduleIntentInHistory) {
                toCancelId =
                        appointmentService.getSingleActiveAppointmentId(
                                tenantId, conversationId.value(), calendarZone);
            }
            if (serviceNameOpt.isEmpty() && toCancelId.isPresent()) {
                serviceNameOpt =
                        appointmentService.findServiceNameForActiveAppointment(
                                tenantId, toCancelId.get(), conversationId.value(), calendarZone);
            }
            if (serviceNameOpt.isEmpty()) {
                Optional<String> inferredUnknownService =
                        SchedulingExplicitTimeShortcut.recoverServiceHintFromUserHistory(historyForAi)
                                .filter(s -> !appointmentService.isServiceInTenantCatalog(tenantId, s));
                if (inferredUnknownService.isPresent()) {
                    String msg =
                            appointmentService.buildUnknownServiceReplyWithOptions(
                                    tenantId, inferredUnknownService.get());
                    Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
                    ConversationContext updatedAsk = context.append(userMessage, assistantMessage);
                    conversationContextStore.save(updatedAsk);
                    return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg));
                }
                String schedulingLabel =
                        toCancelId.isPresent() ? "reagendamento" : "agendamento";
                String ask =
                        "Não encontrei no catálogo um serviço válido para este pedido. "
                                + "Para continuar o "
                                + schedulingLabel
                                + ", escolha primeiro o serviço desejado. "
                                + "Responda com o número de uma opção abaixo "
                                + "ou o nome exato do serviço.\n\n"
                                + appointmentService.listTenantServicesForScheduling(tenantId);
                Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(ask));
                ConversationContext updatedAsk = context.append(userMessage, assistantMessage);
                conversationContextStore.save(updatedAsk);
                return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(ask));
            }
            String serviceName = serviceNameOpt.get();
            if (!appointmentService.isServiceInTenantCatalog(tenantId, serviceName)) {
                String msg = appointmentService.buildUnknownServiceReplyWithOptions(tenantId, serviceName);
                Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
                ConversationContext updatedAsk = context.append(userMessage, assistantMessage);
                conversationContextStore.save(updatedAsk);
                return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg));
            }
            if (toCancelId.isPresent()) {
                Optional<String> sameDayPastTimeError =
                        rejectIfSameDayTimeAlreadyPassed(choice.date(), choice.timeHhMm(), calendarZone);
                if (sameDayPastTimeError.isPresent()) {
                    String msg = sameDayPastTimeError.get();
                    Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
                    ConversationContext updatedFail = context.append(userMessage, assistantMessage);
                    conversationContextStore.save(updatedFail);
                    return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg));
                }
                LOG.info(
                        "[scheduling-reschedule] confirmação de novo horário após indisponibilidade; a cancelar agendamento anterior antes de criar novo (tenant={} appointmentId={})",
                        tenantId.value(),
                        toCancelId.get());
                String cancelReply =
                        appointmentService.cancelAppointment(
                                tenantId,
                                conversationId.value(),
                                "",
                                String.valueOf(toCancelId.get()),
                                calendarZone);
                if (!AppointmentService.isSuccessfulCancellationReply(cancelReply)) {
                    LOG.warn(
                            "[scheduling-reschedule] cancelamento prévio falhou; criação abortada (tenant={} appointmentId={} reply={})",
                            tenantId.value(),
                            toCancelId.get(),
                            cancelReply);
                    Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(cancelReply));
                    ConversationContext updatedFail = context.append(userMessage, assistantMessage);
                    conversationContextStore.save(updatedFail);
                    return new ChatResult(
                            SchedulingUserReplyNormalizer.stripInternalSlotAppendix(cancelReply));
                }
            }
            LOG.info(
                    "[scheduling-bypass] create_appointment directo no ChatService (sem motor de IA) tenant={} date={} time={}",
                    tenantId.value(),
                    iso,
                    choice.timeHhMm());
            Optional<String> sameDayPastTimeError =
                    rejectIfSameDayTimeAlreadyPassed(choice.date(), choice.timeHhMm(), calendarZone);
            if (sameDayPastTimeError.isPresent()) {
                String msg = sameDayPastTimeError.get();
                Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
                ConversationContext updatedFail = context.append(userMessage, assistantMessage);
                conversationContextStore.save(updatedFail);
                return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg));
            }
            String result =
                    appointmentService.createAppointment(
                            tenantId,
                            iso,
                            choice.timeHhMm(),
                            clientName,
                            serviceName,
                            conversationId.value(),
                            calendarZone);
            String r = result != null ? result.strip() : "";
            String forHistory = r;
            if (whatsappSuppressInChatWhenNotifying && SchedulingCreateAppointmentResult.isSuccess(r)) {
                forHistory =
                        "Agendamento confirmado. O cliente recebeu a confirmação automática no WhatsApp.";
            }
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(forHistory));
            ConversationContext updatedConfirm = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updatedConfirm);
            String outbound =
                    whatsappSuppressInChatWhenNotifying && SchedulingCreateAppointmentResult.isSuccess(r) ? "" : r;
            return new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(outbound));
        }

        var aiRequest = new AICompletionRequest(
                tenantId,
                historyForAi,
                knowledgeHits,
                userMessageForAi,
                systemPrompt,
                provider,
                resumeAfterHuman,
                schedulingTools,
                conversationId.value(),
                crmContext,
                slotExpansion.enforcedChoice(),
                slotExpansion.blockCreateAppointmentThisTurn(),
                schedulingSlotAnchorDate);

        var aiResponse = aiEngine.complete(aiRequest);

        String assistantContent = aiResponse.content();
        Optional<String> outboundOverride = aiResponse.outboundWhatsappTextOverride();
        String outboundForWhatsapp = outboundOverride.orElse(assistantContent);
        if (schedulingTools && aiResponse.whatsAppInteractive().isPresent()) {
            var w = aiResponse.whatsAppInteractive().get();
            if (w.slotTimes() != null && !w.slotTimes().isEmpty()) {
                assistantContent =
                        SchedulingUserReplyNormalizer.appendSchedulingAppendices(
                                assistantContent, w.slotTimes(), w.requestedDate());
                if (outboundOverride.isEmpty()) {
                    outboundForWhatsapp = assistantContent;
                }
            }
        }
        if (schedulingTools && slotExpansion.pendingConfirmationDraft().isPresent()) {
            assistantContent =
                    SchedulingUserReplyNormalizer.appendSchedulingDraft(
                            assistantContent, slotExpansion.pendingConfirmationDraft().get());
            if (outboundOverride.isEmpty()) {
                outboundForWhatsapp = assistantContent;
            }
        }
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(assistantContent));

        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);

        return new ChatResult(
                outboundForWhatsapp, aiResponse.whatsAppInteractive(), aiResponse.additionalOutboundMessages());

    }



    /**

     * Texto curto para o system prompt; {@code null} se não houver dados CRM.

     */

    static String buildCrmContextForPrompt(

            String conversationId,

            Optional<CrmCustomerRecord> crmRow,

            Optional<TenantAppointmentListItem> lastAppointment,

            String zoneIdStr) {

        if (conversationId == null || conversationId.isBlank() || crmRow.isEmpty()) {

            return null;

        }

        CrmCustomerRecord c = crmRow.get();

        ZoneId z = ZoneId.of(zoneIdStr);

        var fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

        StringBuilder sb = new StringBuilder();

        if (c.fullName() != null && !c.fullName().isBlank()) {

            sb.append("Nome do cliente: ").append(c.fullName().strip()).append(". ");

        }

        sb.append("Agendamentos registados no CRM: ").append(c.totalAppointments()).append(". ");

        lastAppointment.ifPresent(

                a -> sb.append("Histórico no CRM — último agendamento registado: serviço \"")

                        .append(a.serviceName())

                        .append("\" em ")

                        .append(a.startsAt().atZone(z).format(fmt))

                        .append(" (não confundir com a data desta nova marcação que o cliente estiver a pedir). "));

        if (c.internalNotes() != null && !c.internalNotes().isBlank()) {

            sb.append("Notas internas: ").append(c.internalNotes().strip()).append(". ");

        }

        if ("HOT_LEAD".equals(c.intentStatus())) {

            sb.append("Estado comercial: oportunidade quente — recuperação prioritária. ");

        } else if ("PENDING_LEAD".equals(c.intentStatus())) {

            sb.append("Estado comercial: lead pendente (oportunidade de follow-up). ");

        }

        String detectedIntent =
                c.lastDetectedIntent() != null && !c.lastDetectedIntent().isBlank()
                        ? c.lastDetectedIntent()
                        : c.lastIntent();

        if (detectedIntent != null && !detectedIntent.isBlank()) {

            sb.append("Última intenção detectada: ").append(detectedIntent.strip()).append(". ");

        }

        sb.append(

                "Pode cumprimentar pelo nome quando adequado e sugerir continuidade (ex.: revisão) com base nestes dados, "

                        + "sem inventar factos não indicados aqui.");

        return sb.toString();

    }



    private static List<Message> takeLast(List<Message> messages, int max) {

        if (messages == null || messages.isEmpty()) {

            return List.of();

        }

        if (messages.size() <= max) {

            return List.copyOf(messages);

        }

        return List.copyOf(messages.subList(messages.size() - max, messages.size()));

    }

    /**
     * A linha «Entendido! opção N… [scheduling_draft:…]» não inclui serviço; o «sim» seguinte precisa de
     * {@code [selected_service:…]} (ou eco inválido). Reutiliza a escolha anterior do histórico.
     */
    private static String reattachSelectedServiceToSlotConfirmMessageIfMissing(
            String stored, List<Message> history) {
        if (stored == null
                || stored.isBlank()
                || !stored.contains(SchedulingUserReplyNormalizer.SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
            return stored;
        }
        if (stored.contains(SchedulingUserReplyNormalizer.SELECTED_SERVICE_APPENDIX_TOKEN)) {
            return stored;
        }
        return SchedulingUserReplyNormalizer.parseLastSelectedServiceFromHistory(history)
                .map(sv -> SchedulingUserReplyNormalizer.appendSelectedService(stored, sv))
                .orElse(stored);
    }

    private static Optional<String> findRecentRescheduleRequestFromHistory(
            List<Message> history, ZoneId calendarZone) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        int lowerBound = Math.max(0, history.size() - 12);
        for (int i = history.size() - 1; i >= lowerBound; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            String content = m.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            String stripped = content.strip();
            if (!SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent(stripped)) {
                continue;
            }
            if (SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(stripped, calendarZone).isPresent()) {
                return Optional.of(stripped);
            }
        }
        return Optional.empty();
    }

    /**
     * Sanity check para STT: quando o áudio «5» vira «sim» e o fluxo actual espera número/opção.
     */
    private Optional<ChatResult> maybeHandleLikelyMisheardNumericChoice(
            boolean schedulingTools,
            String userText,
            List<Message> historyForAi,
            ConversationContext context,
            Message userMessage) {
        if (!schedulingTools || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        String stripped = userText.strip();
        if (!SHORT_CONFIRMATION.matcher(stripped).matches()) {
            return Optional.empty();
        }
        if (SchedulingUserReplyNormalizer.parseLastDraftFromHistory(historyForAi).isPresent()) {
            return Optional.empty();
        }
        if (lastAssistantAskedForSchedulingConfirmation(historyForAi)) {
            return Optional.empty();
        }
        boolean expectsNumericChoice =
                SchedulingUserReplyNormalizer.shouldInterpretNumericChoiceAsServiceSelection(historyForAi)
                        || SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(historyForAi)
                        || lastAssistantAskedForNumberChoice(historyForAi);
        if (!expectsNumericChoice) {
            return Optional.empty();
        }
        String msg =
                "Só para confirmar: você quis dizer o número de uma opção (por exemplo, *5*)? "
                        + "Se sim, responda apenas com o número.";
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg)));
    }

    private static boolean lastAssistantAskedForNumberChoice(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String c = m.content().toLowerCase(Locale.ROOT);
            if (c.contains(SchedulingUserReplyNormalizer.SCHEDULING_DRAFT_APPENDIX_TOKEN.toLowerCase(Locale.ROOT))) {
                return false;
            }
            return c.contains("responda com o número")
                    || c.contains("responda com o numero")
                    || c.contains("diga apenas o código")
                    || c.contains("diga apenas o codigo")
                    || c.contains(SchedulingUserReplyNormalizer.SERVICE_OPTION_MAP_APPENDIX_TOKEN.toLowerCase(Locale.ROOT))
                    || c.contains(CancelOptionMap.APPENDIX_PREFIX.toLowerCase(Locale.ROOT));
        }
        return false;
    }

    private static boolean lastAssistantAskedForSchedulingConfirmation(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String c = m.content().toLowerCase(Locale.ROOT);
            if (c.contains(SchedulingUserReplyNormalizer.SERVICE_OPTION_MAP_APPENDIX_TOKEN.toLowerCase(Locale.ROOT))
                    || c.contains(CancelOptionMap.APPENDIX_PREFIX.toLowerCase(Locale.ROOT))) {
                return false;
            }
            return c.contains("posso confirmar o agendamento")
                    || c.contains("antes de concluir o agendamento")
                    || c.contains("deseja confirmar o agendamento")
                    || c.contains("quer confirmar o agendamento");
        }
        return false;
    }

    private static Optional<SchedulingEnforcedChoice> maybeRecoverEnforcedChoiceFromHistoryOnConfirmation(
            boolean schedulingTools,
            String userText,
            List<Message> historyForAi,
            ZoneId calendarZone) {
        if (!schedulingTools || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        String normalized = userText.strip();
        if (!SHORT_CONFIRMATION.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return SchedulingExplicitTimeShortcut.recoverEnforcedChoiceFromUserHistory(historyForAi, calendarZone);
    }

    private static boolean isShortConfirmationMessage(String userText) {
        if (userText == null || userText.isBlank()) {
            return false;
        }
        return SHORT_CONFIRMATION.matcher(userText.strip()).matches();
    }

    private static Optional<Long> findRecentRescheduleAppointmentIdFromHistory(
            List<Message> history, ZoneId calendarZone, String currentUserText) {
        if (history == null || history.isEmpty() || calendarZone == null) {
            return Optional.empty();
        }
        if (currentUserText != null
                && SchedulingUserReplyNormalizer.looksLikeNewAppointmentBookingRequest(currentUserText)) {
            return Optional.empty();
        }
        int lowerBound = Math.max(0, history.size() - 12);
        for (int i = history.size() - 1; i >= lowerBound; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            String content = m.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            Optional<Long> from =
                    SchedulingRescheduleIdExtractor.extractFromUserText(content.strip(), calendarZone);
            if (from.isPresent()) {
                return from;
            }
        }
        return Optional.empty();
    }

    private Optional<ChatResult> maybePrepareRescheduleDraftForExplicitTime(
            boolean schedulingTools,
            com.atendimento.cerebro.domain.tenant.TenantId tenantId,
            String conversationId,
            String userText,
            ConversationContext context,
            Message userMessage,
            List<Message> historyForAi,
            ZoneId calendarZone) {
        if (!schedulingTools || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        Optional<SchedulingEnforcedChoice> explicit =
                SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                        userText.strip(), calendarZone)
                        .or(() -> parseTodayTimeFallback(userText, calendarZone));
        if (explicit.isEmpty()) {
            return Optional.empty();
        }
        Optional<Long> appointmentId =
                findRecentRescheduleAppointmentIdFromHistory(historyForAi, calendarZone, userText);
        if (appointmentId.isEmpty()) {
            appointmentId =
                    appointmentService.getSingleActiveAppointmentId(tenantId, conversationId, calendarZone);
        }
        if (appointmentId.isEmpty()) {
            return Optional.empty();
        }
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        Optional<TenantAppointmentListItem> active =
                tenantAppointmentQuery.findByIdForTenantAndConversation(
                        tenantId, appointmentId.get(), conversationId, zoneId);
        if (active.isEmpty()
                || active.get().bookingStatus() != TenantAppointmentListItem.BookingStatus.AGENDADO) {
            return Optional.empty();
        }
        String service = active.get().serviceName() != null ? active.get().serviceName().strip() : "";
        if (service.isBlank() || !appointmentService.isServiceInTenantCatalog(tenantId, service)) {
            return Optional.empty();
        }
        SchedulingEnforcedChoice target = explicit.get();
        String dayPt =
                target.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")));
        String hardcoded =
                "Perfeito! O serviço *"
                        + service
                        + "* para *"
                        + dayPt
                        + "* às *"
                        + target.timeHhMm()
                        + "*. Posso confirmar o agendamento?";
        String stored =
                SchedulingUserReplyNormalizer.appendSelectedService(
                        SchedulingUserReplyNormalizer.appendSchedulingDraft(hardcoded, target), service);
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored)));
    }

    private static Optional<SchedulingEnforcedChoice> parseTodayTimeFallback(String userText, ZoneId calendarZone) {
        if (userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        if (!lower.contains("hoje")) {
            return Optional.empty();
        }
        Optional<String> hhMmOpt = SchedulingExplicitTimeShortcut.parseTimeHhMmFromUserText(userText);
        if (hhMmOpt.isEmpty()) {
            return Optional.empty();
        }
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        LocalDate day = LocalDate.now(z);
        return Optional.of(new SchedulingEnforcedChoice(day, hhMmOpt.get()));
    }

    private Optional<ChatResult> maybeAskRescheduleDateTimeForExplicitAppointmentId(
            boolean schedulingTools,
            com.atendimento.cerebro.domain.tenant.TenantId tenantId,
            String conversationId,
            String userText,
            ConversationContext context,
            Message userMessage,
            List<Message> historyForAi,
            ZoneId calendarZone) {
        if (!schedulingTools || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        Optional<Long> idOpt = SchedulingRescheduleIdExtractor.extractFromUserText(userText.strip(), calendarZone);
        if (idOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<SchedulingEnforcedChoice> explicit =
                SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                userText.strip(), calendarZone)
                        .or(() -> parseTodayTimeFallback(userText, calendarZone));
        String normalized = userText.toLowerCase(Locale.ROOT);
        boolean sameDayWithClock =
                (normalized.contains("mesmo dia")
                                || normalized.contains("do mesmo dia")
                                || normalized.contains("no mesmo dia"))
                        && CLOCK_TIME.matcher(userText).find();
        if (explicit.isPresent()) {
            return Optional.empty();
        }
        if (sameDayWithClock) {
            return Optional.empty();
        }
        long appointmentId = idOpt.get();
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        Optional<TenantAppointmentListItem> active =
                tenantAppointmentQuery.findByIdForTenantAndConversation(
                        tenantId, appointmentId, conversationId, zoneId);
        if (active.isEmpty() || active.get().bookingStatus() != TenantAppointmentListItem.BookingStatus.AGENDADO) {
            return Optional.empty();
        }
        String service = active.get().serviceName() != null ? active.get().serviceName().strip() : "";
        String ask =
                service.isBlank()
                        ? "Perfeito, vamos reagendar o atendimento *"
                                + appointmentId
                                + "*. Informe a nova data e horário desejados (ex.: hoje 11:00)."
                        : "Perfeito, vamos reagendar o *"
                                + service
                                + "* (ID "
                                + appointmentId
                                + "). Informe a nova data e horário desejados (ex.: hoje 11:00).";
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(ask));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(ask)));
    }

    private Optional<ChatResult> maybeContinueSchedulingAfterPastTimeRejection(
            boolean schedulingTools,
            com.atendimento.cerebro.domain.tenant.TenantId tenantId,
            String userText,
            List<Message> historyForAi,
            ConversationContext context,
            Message userMessage,
            ZoneId calendarZone) {
        if (!schedulingTools || userText == null || userText.isBlank() || historyForAi == null || historyForAi.isEmpty()) {
            return Optional.empty();
        }
        String stripped = userText.strip();
        if (!CLOCK_TIME_ONLY.matcher(stripped).matches()) {
            return Optional.empty();
        }
        Message lastAssistant = null;
        for (int i = historyForAi.size() - 1; i >= 0; i--) {
            Message m = historyForAi.get(i);
            if (m.role() == MessageRole.ASSISTANT && m.content() != null && !m.content().isBlank()) {
                lastAssistant = m;
                break;
            }
        }
        if (lastAssistant == null) {
            return Optional.empty();
        }
        String lastAssistantText = lastAssistant.content().toLowerCase(Locale.ROOT);
        if (!lastAssistantText.contains("esse horário já passou para hoje")
                && !lastAssistantText.contains("esse horario ja passou para hoje")) {
            return Optional.empty();
        }
        String timeCanon = SchedulingSlotCapture.normalizeSingleSlotToken(stripped);
        if (timeCanon == null) {
            return Optional.empty();
        }
        Optional<String> serviceOpt = SchedulingExplicitTimeShortcut.parseServiceNameForCreateFromHistory(historyForAi);
        if (serviceOpt.isEmpty()) {
            return Optional.empty();
        }
        LocalDate day = LocalDate.now(calendarZone != null ? calendarZone : ZoneId.systemDefault());
        String iso = day.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String availability = appointmentScheduling.checkAvailability(tenantId, iso);
        List<String> slots = SchedulingSlotCapture.normalizeSlotTimes(
                SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(availability));
        if (slots.isEmpty()) {
            String msg = "Para hoje não há horários livres neste momento. Posso verificar outro dia?";
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
            ConversationContext updated = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updated);
            return Optional.of(new ChatResult(msg));
        }
        if (!slots.contains(timeCanon)) {
            String intro =
                    "O horário *"
                            + timeCanon
                            + "* não está disponível para hoje. Seguem os horários livres:\n\n"
                            + SchedulingSlotCapture.buildPremiumFormattedSlotList(day, calendarZone, slots);
            String stored = SchedulingUserReplyNormalizer.appendSchedulingAppendices(intro, slots, day);
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
            ConversationContext updated = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updated);
            return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored)));
        }
        String service = serviceOpt.get().strip();
        String dayPt = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")));
        String hardcoded =
                "Perfeito! O serviço *"
                        + service
                        + "* para *"
                        + dayPt
                        + "* às *"
                        + timeCanon
                        + "*. Posso confirmar o agendamento?";
        SchedulingEnforcedChoice draft = new SchedulingEnforcedChoice(day, timeCanon);
        String stored =
                SchedulingUserReplyNormalizer.appendSelectedService(
                        SchedulingUserReplyNormalizer.appendSchedulingDraft(hardcoded, draft), service);
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(stored));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(stored)));
    }

    private Optional<ChatResult> maybeListActiveAppointmentsForRescheduleContext(
            boolean schedulingTools,
            boolean rescheduleIntent,
            com.atendimento.cerebro.domain.tenant.TenantId tenantId,
            String conversationId,
            String userText,
            List<Message> historyForAi,
            ConversationContext context,
            Message userMessage,
            ZoneId calendarZone) {
        if (!schedulingTools || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        String normalized = userText.strip();
        boolean hasExplicitRescheduleId =
                SchedulingRescheduleIdExtractor.extractFromUserText(normalized, calendarZone).isPresent();
        boolean hasExplicitDateTime =
                SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(normalized, calendarZone)
                                .isPresent()
                        || parseTodayTimeFallback(userText, calendarZone).isPresent();
        boolean newBookingMentionedRecently =
                SchedulingUserReplyNormalizer.hasRecentNewAppointmentBookingRequestInHistory(
                        historyForAi, 12);
        boolean listIntentInRescheduleFlow =
                SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent(normalized)
                        && SchedulingUserReplyNormalizer.hasRecentRescheduleUserIntentInHistory(historyForAi, 12)
                        && !newBookingMentionedRecently;
        boolean shouldAutoListForReschedule =
                rescheduleIntent && !hasExplicitRescheduleId && !hasExplicitDateTime;
        if (!listIntentInRescheduleFlow && !shouldAutoListForReschedule) {
            return Optional.empty();
        }
        String listed =
                appointmentService.getActiveAppointments(tenantId, conversationId, calendarZone, true);
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(listed));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(listed)));
    }

    private Optional<ChatResult> maybeExecuteDirectRescheduleWithExplicitAppointmentId(
            boolean schedulingTools,
            com.atendimento.cerebro.domain.tenant.TenantId tenantId,
            String conversationId,
            String userText,
            ConversationContext context,
            Message userMessage,
            Optional<CrmCustomerRecord> crmRow,
            ZoneId calendarZone) {
        if (!schedulingTools
                || userText == null
                || userText.isBlank()
                || conversationId == null
                || conversationId.isBlank()) {
            return Optional.empty();
        }
        Optional<Long> idForReschedule =
                SchedulingRescheduleIdExtractor.extractFromUserText(userText.strip(), calendarZone);
        if (idForReschedule.isEmpty()) {
            return Optional.empty();
        }
        long appointmentId = idForReschedule.get();
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        Optional<TenantAppointmentListItem> target =
                tenantAppointmentQuery.findByIdForTenantAndConversation(
                        tenantId, appointmentId, conversationId, zoneId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        TenantAppointmentListItem current = target.get();
        if (current.bookingStatus() != TenantAppointmentListItem.BookingStatus.AGENDADO) {
            return Optional.empty();
        }
        Optional<SchedulingEnforcedChoice> parsed =
                SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                        userText.strip(), calendarZone);
        Optional<SchedulingEnforcedChoice> targetChoice =
                parsed.isPresent()
                        ? parsed
                        : parseSameDayTargetTimeFromRescheduleText(userText, current, calendarZone);
        if (targetChoice.isEmpty()) {
            return Optional.empty();
        }
        String serviceName = current.serviceName() != null ? current.serviceName().strip() : "";
        if (serviceName.isBlank()) {
            return Optional.empty();
        }
        if (!appointmentService.isServiceInTenantCatalog(tenantId, serviceName)) {
            return Optional.empty();
        }
        SchedulingEnforcedChoice choice = targetChoice.get();
        Optional<String> sameDayPastTimeError =
                rejectIfSameDayTimeAlreadyPassed(choice.date(), choice.timeHhMm(), calendarZone);
        if (sameDayPastTimeError.isPresent()) {
            String msg = sameDayPastTimeError.get();
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(msg));
            ConversationContext updatedFail = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updatedFail);
            return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(msg)));
        }
        String iso = choice.date().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String clientName =
                crmRow.map(CrmCustomerRecord::fullName)
                        .filter(n -> n != null && !n.isBlank())
                        .map(String::strip)
                        .orElseGet(
                                () ->
                                        current.clientName() != null && !current.clientName().isBlank()
                                                ? current.clientName().strip()
                                                : "Cliente");
        LOG.info(
                "[scheduling-reschedule] execução direta por ID explícito (tenant={} appointmentId={} date={} time={})",
                tenantId.value(),
                appointmentId,
                iso,
                choice.timeHhMm());
        String cancelReply =
                appointmentService.cancelAppointment(
                        tenantId,
                        conversationId,
                        "",
                        String.valueOf(appointmentId),
                        calendarZone);
        if (!AppointmentService.isSuccessfulCancellationReply(cancelReply)) {
            Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(cancelReply));
            ConversationContext updatedFail = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updatedFail);
            return Optional.of(
                    new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(cancelReply)));
        }
        String createReply =
                appointmentService.createAppointment(
                        tenantId,
                        iso,
                        choice.timeHhMm(),
                        clientName,
                        serviceName,
                        conversationId,
                        calendarZone);
        String reply = createReply != null ? createReply.strip() : "";
        Message assistantMessage = Message.assistantMessage(forAssistantMessageRecord(reply));
        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);
        return Optional.of(new ChatResult(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(reply)));
    }

    private static Optional<SchedulingEnforcedChoice> parseSameDayTargetTimeFromRescheduleText(
            String userText, TenantAppointmentListItem current, ZoneId calendarZone) {
        if (userText == null || userText.isBlank() || current == null || current.startsAt() == null) {
            return Optional.empty();
        }
        String normalized = userText.toLowerCase(Locale.ROOT);
        boolean sameDayMention =
                normalized.contains("mesmo dia")
                        || normalized.contains("do mesmo dia")
                        || normalized.contains("no mesmo dia");
        if (!sameDayMention) {
            return Optional.empty();
        }
        Matcher tm = CLOCK_TIME.matcher(userText);
        if (!tm.find()) {
            return Optional.empty();
        }
        String hhMm = String.format(Locale.ROOT, "%02d:%02d", Integer.parseInt(tm.group(1)), Integer.parseInt(tm.group(2)));
        LocalDate day =
                ZonedDateTime.ofInstant(current.startsAt(), calendarZone != null ? calendarZone : ZoneId.systemDefault())
                        .toLocalDate();
        if (day.isBefore(LocalDate.now(calendarZone != null ? calendarZone : ZoneId.systemDefault()))) {
            return Optional.empty();
        }
        return Optional.of(new SchedulingEnforcedChoice(day, hhMm));
    }

    private static Optional<String> rejectIfSameDayTimeAlreadyPassed(
            LocalDate day, String timeHhMm, ZoneId calendarZone) {
        if (day == null || timeHhMm == null || timeHhMm.isBlank()) {
            return Optional.empty();
        }
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(z);
        if (!day.equals(now.toLocalDate())) {
            return Optional.empty();
        }
        try {
            LocalDateTime candidate = LocalDateTime.of(day, LocalTime.parse(timeHhMm.strip()));
            if (candidate.isBefore(now)) {
                return Optional.of(
                        "Esse horário já passou para hoje. Pode escolher um horário a partir de agora?");
            }
        } catch (RuntimeException ignored) {
            // Invalid time is handled by downstream validators/tooling.
        }
        return Optional.empty();
    }

    /**
     * Obrigatório após {@code cancel_appointment} concluir com sucesso: liberta listas de horário, mapa opção→ID e
     * {@code waiting_for_cancellation_choice} na sessão HTTP (ThreadLocal).
     */
    public static void resetContext() {
        SchedulingToolContext.resetContext();
    }

    /**
     * Limpa flags de cancelamento e rascunhos de agendamento (ThreadLocal). Usado após saudação no início do turno.
     */
    public void forceResetContext() {
        resetContext();
    }

    static boolean isGreetingMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String t = raw.strip();
        String normalized =
                Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return GREETING_HEAD.matcher(normalized).find();
    }

    /**
     * Encerra o modo cancelamento no pedido actual (ThreadLocal): mapa opção→ID e flag «à espera de escolha». Chame após
     * {@code cancel_appointment} (sucesso ou falha) para não deixar resíduos a bloquear {@code check_availability} no
     * mesmo processamento. O histórico persistido é tratado em {@link #chat(ChatCommand)} (reinício de agendamento /
     * intenção cancelar).
     */
    public static void clearCancellationContext(String conversationId) {
        SchedulingToolContext.resetContext();
        LOG.info(
                "[ContextReset] Estado de cancelamento (ThreadLocal) limpo para a conversa {}",
                conversationId == null || conversationId.isBlank() ? "?" : conversationId.strip());
    }

}

