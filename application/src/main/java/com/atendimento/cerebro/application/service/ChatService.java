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

import com.atendimento.cerebro.domain.conversation.ConversationContext;

import com.atendimento.cerebro.domain.conversation.Message;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;

import com.atendimento.cerebro.domain.tenant.TenantConfiguration;

import com.atendimento.cerebro.application.scheduling.SlotChoiceExpansion;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;

import java.time.ZoneId;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import java.util.Optional;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



/**

 * Ordem do fluxo: (1) contexto, (2) RAG com embeddings Google GenAI ({@code KnowledgeBasePort}),

 * (3) geração da resposta com o motor escolhido em {@link com.atendimento.cerebro.application.dto.ChatCommand#chatProvider()}.

 */

public class ChatService implements ChatUseCase {



    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);



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

    private final String schedulingZoneId;



    public ChatService(

            ConversationContextStorePort conversationContextStore,

            KnowledgeBasePort knowledgeBase,

            AIEnginePort aiEngine,

            TenantConfigurationStorePort tenantConfigurationStore,

            ConversationBotStatePort conversationBotStatePort,

            CrmCustomerStorePort crmCustomerStore,

            CrmCustomerQueryPort crmCustomerQuery,

            TenantAppointmentQueryPort tenantAppointmentQuery,

            String schedulingZoneId) {

        this.conversationContextStore = conversationContextStore;

        this.knowledgeBase = knowledgeBase;

        this.aiEngine = aiEngine;

        this.tenantConfigurationStore = tenantConfigurationStore;

        this.conversationBotStatePort = conversationBotStatePort;

        this.crmCustomerStore = crmCustomerStore;

        this.crmCustomerQuery = crmCustomerQuery;

        this.tenantAppointmentQuery = tenantAppointmentQuery;

        this.schedulingZoneId = schedulingZoneId != null && !schedulingZoneId.isBlank()

                ? schedulingZoneId.strip()

                : "America/Sao_Paulo";

    }



    @Override

    public ChatResult chat(ChatCommand command) {

        var tenantId = command.tenantId();

        var conversationId = command.conversationId();

        var userText = command.userMessage();



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

        String systemPrompt = tenantConfig.map(tc -> tc.systemPrompt().strip()).orElse("");

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

        SlotChoiceExpansion slotExpansion =
                schedulingTools
                        ? SchedulingUserReplyNormalizer.expandNumericSlotChoice(userText, historyForAi)
                        : SlotChoiceExpansion.unchanged(userText);
        String userMessageForAi = slotExpansion.expandedUserMessage();



        String crmContext =

                buildCrmContextForPrompt(

                        conversationId.value(),

                        crmCustomerQuery.findByTenantAndConversationId(tenantId, conversationId.value()),

                        tenantAppointmentQuery.findMostRecentByConversationId(

                                tenantId, conversationId.value(), schedulingZoneId),

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
            Message assistantMessage = Message.assistantMessage(stored);
            ConversationContext updated = context.append(userMessage, assistantMessage);
            conversationContextStore.save(updated);
            return new ChatResult(hardcoded);
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
                slotExpansion.blockCreateAppointmentThisTurn());

        var aiResponse = aiEngine.complete(aiRequest);

        String assistantContent = aiResponse.content();
        if (schedulingTools && aiResponse.whatsAppInteractive().isPresent()) {
            var w = aiResponse.whatsAppInteractive().get();
            if (w.slotTimes() != null && !w.slotTimes().isEmpty()) {
                assistantContent =
                        SchedulingUserReplyNormalizer.appendSchedulingAppendices(
                                assistantContent, w.slotTimes(), w.requestedDate());
            }
        }
        if (schedulingTools && slotExpansion.pendingConfirmationDraft().isPresent()) {
            assistantContent =
                    SchedulingUserReplyNormalizer.appendSchedulingDraft(
                            assistantContent, slotExpansion.pendingConfirmationDraft().get());
        }
        Message assistantMessage = Message.assistantMessage(assistantContent);

        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);

        return new ChatResult(
                assistantContent, aiResponse.whatsAppInteractive(), aiResponse.additionalOutboundMessages());

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

}

