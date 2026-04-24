package com.atendimento.cerebro.application.service;



import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.tuple;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.inOrder;

import static org.mockito.Mockito.never;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;



import com.atendimento.cerebro.application.ai.AiChatProvider;

import com.atendimento.cerebro.application.dto.AICompletionRequest;

import com.atendimento.cerebro.application.dto.AICompletionResponse;

import com.atendimento.cerebro.application.dto.ChatCommand;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;

import com.atendimento.cerebro.application.port.out.AIEnginePort;

import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;

import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;

import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;

import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;

import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;

import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;

import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;

import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;

import com.atendimento.cerebro.domain.conversation.ConversationContext;

import com.atendimento.cerebro.domain.conversation.ConversationId;

import com.atendimento.cerebro.domain.conversation.Message;

import com.atendimento.cerebro.domain.conversation.MessageRole;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;

import com.atendimento.cerebro.domain.tenant.TenantId;

import java.time.Instant;

import java.util.List;

import java.util.Optional;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;



@ExtendWith(MockitoExtension.class)

class ChatServiceTest {



    @Mock

    private ConversationContextStorePort conversationContextStore;



    @Mock

    private KnowledgeBasePort knowledgeBase;



    @Mock

    private AIEnginePort aiEngine;



    @Mock

    private TenantConfigurationStorePort tenantConfigurationStore;



    @Mock

    private ConversationBotStatePort conversationBotStatePort;



    @Mock

    private CrmCustomerStorePort crmCustomerStore;



    @Mock

    private CrmCustomerQueryPort crmCustomerQuery;



    @Mock

    private TenantAppointmentQueryPort tenantAppointmentQuery;



    @Mock

    private TenantAppointmentStorePort tenantAppointmentStore;



    @Mock

    private AppointmentSchedulingPort appointmentScheduling;



    @Mock

    private ApplicationEventPublisher applicationEventPublisher;



    private AppointmentService appointmentService;



    private ChatService chatService;



    private TenantId tenantId;

    private ConversationId conversationId;



    @BeforeEach

    void setUp() {

        tenantId = new TenantId("tenant-1");

        conversationId = new ConversationId("conv-1");

        appointmentService =
                new AppointmentService(
                        tenantAppointmentQuery,
                        tenantAppointmentStore,
                        appointmentScheduling,
                        crmCustomerQuery,
                        applicationEventPublisher);

        chatService = new ChatService(

                conversationContextStore,

                knowledgeBase,

                aiEngine,

                tenantConfigurationStore,

                conversationBotStatePort,

                crmCustomerStore,

                crmCustomerQuery,

                tenantAppointmentQuery,

                appointmentScheduling,

                appointmentService,

                "America/Sao_Paulo",
                false);

        when(crmCustomerQuery.findByTenantAndConversationId(any(), any())).thenReturn(Optional.empty());

        when(tenantAppointmentQuery.findMostRecentByConversationId(any(), any(), any()))

                .thenReturn(Optional.empty());

    }



    @Test

    void chat_createsContextWhenMissing_loadsKb_callsAi_savesWithBothMessages() {

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.empty());

        when(knowledgeBase.findTopThreeRelevantFragments(eq(tenantId), eq("hello")))

                .thenReturn(List.of(new KnowledgeHit("k1", "snippet", 0.9)));

        when(aiEngine.complete(any(AICompletionRequest.class)))

                .thenReturn(new AICompletionResponse("hi there"));



        var result = chatService.chat(new ChatCommand(tenantId, conversationId, "hello"));



        assertThat(result.assistantMessage()).isEqualTo("hi there");



        ArgumentCaptor<ConversationContext> saved = ArgumentCaptor.forClass(ConversationContext.class);

        verify(conversationContextStore).save(saved.capture());

        ConversationContext ctx = saved.getValue();

        assertThat(ctx.getMessages()).hasSize(2);

        assertThat(ctx.getMessages().get(0).role()).isEqualTo(MessageRole.USER);

        assertThat(ctx.getMessages().get(0).content()).isEqualTo("hello");

        assertThat(ctx.getMessages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);

        assertThat(ctx.getMessages().get(1).content()).isEqualTo("hi there");



        verify(crmCustomerStore).ensureOnConversationStart(tenantId, "conv-1", Optional.empty());

        var ordered = inOrder(crmCustomerStore, conversationContextStore, knowledgeBase, aiEngine);

        ordered.verify(crmCustomerStore).ensureOnConversationStart(tenantId, "conv-1", Optional.empty());

        ordered.verify(conversationContextStore).load(tenantId, conversationId);

        ordered.verify(knowledgeBase).findTopThreeRelevantFragments(tenantId, "hello");

        ordered.verify(aiEngine).complete(any(AICompletionRequest.class));

        ordered.verify(conversationContextStore).save(any(ConversationContext.class));

    }



    @Test

    void chat_appendsToExistingHistory() {

        ConversationContext existing = ConversationContext.builder()

                .tenantId(tenantId)

                .conversationId(conversationId)

                .messages(List.of(Message.userMessage("prev")))

                .build();

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));

        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "next")).thenReturn(List.of());

        when(aiEngine.complete(any(AICompletionRequest.class)))

                .thenReturn(new AICompletionResponse("ok"));



        chatService.chat(new ChatCommand(tenantId, conversationId, "next", 3, AiChatProvider.GEMINI));



        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);

        verify(aiEngine).complete(aiReq.capture());

        assertThat(aiReq.getValue().conversationHistory())

                .extracting(Message::role, Message::content)

                .containsExactly(tuple(MessageRole.USER, "prev"));



        ArgumentCaptor<ConversationContext> saved = ArgumentCaptor.forClass(ConversationContext.class);

        verify(conversationContextStore).save(saved.capture());

        assertThat(saved.getValue().getMessages()).hasSize(3);

        assertThat(saved.getValue().getMessages().get(2).content()).isEqualTo("ok");

    }



    @Test

    void chat_gemini_alwaysEnablesSchedulingTools_forPersistenceEvenWithoutGoogleCalendarId() {

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.empty());

        when(knowledgeBase.findTopThreeRelevantFragments(eq(tenantId), eq("hi"))).thenReturn(List.of());

        when(aiEngine.complete(any(AICompletionRequest.class))).thenReturn(new AICompletionResponse("ok"));



        chatService.chat(new ChatCommand(tenantId, conversationId, "hi", null, AiChatProvider.GEMINI));



        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);

        verify(aiEngine).complete(aiReq.capture());

        assertThat(aiReq.getValue().schedulingToolsEnabled()).isTrue();

    }



    @Test

    void chat_whenConversationStoreEmpty_usesWhatsAppHistoryFallback() {

        ConversationContext emptyStore =

                ConversationContext.builder()

                        .tenantId(tenantId)

                        .conversationId(conversationId)

                        .messages(List.of())

                        .build();

        List<Message> wa = List.of(Message.userMessage("wa-a"), Message.assistantMessage("wa-b"));

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(emptyStore));

        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "next")).thenReturn(List.of());

        when(aiEngine.complete(any(AICompletionRequest.class)))

                .thenReturn(new AICompletionResponse("ok"));



        chatService.chat(

                new ChatCommand(

                        tenantId, conversationId, "next", null, AiChatProvider.GEMINI, wa));



        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);

        verify(aiEngine).complete(aiReq.capture());

        assertThat(aiReq.getValue().conversationHistory()).isEqualTo(wa);

    }



    @Test

    void chat_passesCrmContextToAiWhenCustomerExists() {

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.empty());

        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "oi")).thenReturn(List.of());

        when(aiEngine.complete(any(AICompletionRequest.class))).thenReturn(new AICompletionResponse("ok"));

        when(crmCustomerQuery.findByTenantAndConversationId(tenantId, "conv-1"))

                .thenReturn(

                        Optional.of(

                                new CrmCustomerRecord(

                                        UUID.randomUUID(),

                                        "tenant-1",

                                        "conv-1",

                                        "5511",

                                        "João",

                                        null,

                                        Instant.parse("2025-01-01T12:00:00Z"),

                                        2,

                                        "prefere óleo sintético",

                                        "Orçamento",

                                        "Orçamento",

                                        74,

                                        false,

                                        "NONE",

                                        null)));



        chatService.chat(new ChatCommand(tenantId, conversationId, "oi", null, AiChatProvider.GEMINI));



        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);

        verify(aiEngine).complete(aiReq.capture());

        assertThat(aiReq.getValue().crmContext()).contains("João").contains("prefere óleo sintético");

    }



    @Test

    void chat_schedulingBypass_numberSelection_skipsAiEngine() {

        Message histMsg =
                Message.assistantMessage(
                        "Horários disponíveis:\n\n[slot_options:09:00,09:30,10:00]\n[slot_date:2026-04-14]");
        ConversationContext existing =
                ConversationContext.builder()
                        .tenantId(tenantId)
                        .conversationId(conversationId)
                        .messages(List.of(histMsg))
                        .build();
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "2")).thenReturn(List.of());

        var result = chatService.chat(
                new ChatCommand(tenantId, conversationId, "2", null, AiChatProvider.GEMINI));

        verify(aiEngine, never()).complete(any());
        assertThat(result.assistantMessage())
                .contains("opção 2")
                .contains("09:30")
                .contains("14/04/2026")
                .contains("Posso confirmar");
        assertThat(result.whatsAppInteractive()).isEmpty();

        ArgumentCaptor<ConversationContext> saved = ArgumentCaptor.forClass(ConversationContext.class);
        verify(conversationContextStore).save(saved.capture());
        String storedAssistant = saved.getValue().getMessages().get(saved.getValue().getMessages().size() - 1).content();
        assertThat(storedAssistant).contains("[scheduling_draft:2026-04-14|09:30]");

    }

    @Test
    void chat_schedulingBypass_simConfirmation_skipsAiEngine_andCallsCalendar() {
        Message assistantDraft =
                Message.assistantMessage(
                        "Ótimo! O horário *17:30* está disponível para *revisão*.\n\n[scheduling_draft:2026-04-16|17:30]");
        ConversationContext existing =
                ConversationContext.builder()
                        .tenantId(tenantId)
                        .conversationId(conversationId)
                        .messages(List.of(assistantDraft))
                        .build();
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "sim")).thenReturn(List.of());
        when(appointmentScheduling.createAppointment(
                        eq(tenantId),
                        eq("2026-04-16"),
                        eq("17:30"),
                        eq("Cliente"),
                        eq("revisão"),
                        eq(conversationId.value())))
                .thenReturn(
                        CreateAppointmentResult.success(
                                "Agendamento confirmado para 16/04/2026 às 17:30. O horário foi registado na agenda da oficina.",
                                1L));

        var result =
                chatService.chat(new ChatCommand(tenantId, conversationId, "sim", null, AiChatProvider.GEMINI));

        verify(aiEngine, never()).complete(any());
        verify(appointmentScheduling)
                .createAppointment(
                        tenantId, "2026-04-16", "17:30", "Cliente", "revisão", conversationId.value());
        assertThat(result.assistantMessage()).contains("Agendamento confirmado");
    }

    @Test
    void chat_rescheduleIntent_doesNotUseExplicitTimeShortcutHardcodedPath() {
        ConversationContext existing =
                ConversationContext.builder()
                        .tenantId(tenantId)
                        .conversationId(conversationId)
                        .messages(List.of(Message.assistantMessage("Sem contexto prévio de opções")))
                        .build();
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(
                        tenantId, "Gostaria de reagendar o atendimento amanhã das 11:00 para as 12:00"))
                .thenReturn(List.of());
        when(aiEngine.complete(any(AICompletionRequest.class))).thenReturn(new AICompletionResponse("ok"));

        var result =
                chatService.chat(
                        new ChatCommand(
                                tenantId,
                                conversationId,
                                "Gostaria de reagendar o atendimento amanhã das 11:00 para as 12:00",
                                null,
                                AiChatProvider.GEMINI));

        verify(aiEngine).complete(any(AICompletionRequest.class));
        assertThat(result.assistantMessage()).isEqualTo("ok");
    }

    @Test
    void chat_schedulingBypass_simAfterRescheduleOption_cancelsPreviousBeforeCreate() {
        TenantAppointmentListItem active =
                new TenantAppointmentListItem(
                        42L,
                        tenantId.value(),
                        conversationId.value(),
                        "Anderson Nunes",
                        "Revisão",
                        Instant.parse("2026-04-24T13:30:00Z"),
                        Instant.parse("2026-04-24T14:00:00Z"),
                        "evt-42",
                        Instant.parse("2026-04-23T12:00:00Z"),
                        TenantAppointmentListItem.AppointmentStatus.UPCOMING,
                        TenantAppointmentListItem.BookingStatus.AGENDADO);
        ConversationContext existing =
                ConversationContext.builder()
                        .tenantId(tenantId)
                        .conversationId(conversationId)
                        .messages(
                                List.of(
                                        Message.userMessage(
                                                "Gostaria de solicitar um reagendamento do atendimento de amanhã às 10:30 para as 13:30"),
                                        Message.assistantMessage(
                                                "Disponibilidade para amanhã:\n\n[slot_options:09:00,09:30,10:00,10:30,11:00,11:30,12:00,13:00]\n[slot_date:2026-04-24]"),
                                        Message.userMessage("8"),
                                        Message.assistantMessage(
                                                "Entendido! Posso confirmar?\n\n[scheduling_draft:2026-04-24|13:00]")))
                        .build();
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "sim")).thenReturn(List.of());
        when(tenantAppointmentQuery.listAgendadoByConversationOrderedAscending(
                        eq(tenantId), eq(conversationId.value()), any()))
                .thenReturn(List.of(active));
        when(tenantAppointmentQuery.findByIdForTenantAndConversation(
                        eq(tenantId), eq(42L), eq(conversationId.value()), any()))
                .thenReturn(Optional.of(active));
        when(appointmentScheduling.deleteCalendarEvent(eq(tenantId), eq("evt-42"))).thenReturn(true);
        when(tenantAppointmentStore.markCancelled(eq(42L), any(Instant.class))).thenReturn(true);
        when(appointmentScheduling.createAppointment(
                        eq(tenantId),
                        eq("2026-04-24"),
                        eq("13:00"),
                        eq("Cliente"),
                        eq("Serviço"),
                        eq(conversationId.value())))
                .thenReturn(
                        CreateAppointmentResult.success(
                                "Agendamento confirmado para 24/04/2026 às 13:00. O horário foi registado na agenda da oficina.",
                                99L));

        var result =
                chatService.chat(new ChatCommand(tenantId, conversationId, "sim", null, AiChatProvider.GEMINI));

        verify(aiEngine, never()).complete(any());
        var order = inOrder(appointmentScheduling);
        order.verify(appointmentScheduling).deleteCalendarEvent(tenantId, "evt-42");
        order.verify(appointmentScheduling)
                .createAppointment(tenantId, "2026-04-24", "13:00", "Cliente", "Serviço", conversationId.value());
        assertThat(result.assistantMessage()).contains("Agendamento confirmado");
    }

}


