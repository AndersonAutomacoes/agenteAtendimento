package com.atendimento.cerebro.application.dto;



import com.atendimento.cerebro.application.ai.AiChatProvider;

import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;

import com.atendimento.cerebro.domain.conversation.Message;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;

import com.atendimento.cerebro.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;



/**

 * @param systemPrompt persona do tenant (vazio se não configurada na base).

 * @param chatProvider motor de chat a usar; {@code null} usa o default da aplicação ({@code cerebro.ai.default-chat-provider}).

 * @param resumeAfterHumanIntervention instrução extra no system prompt após reativar a IA (uma vez).

 * @param schedulingToolsEnabled expõe ferramentas de calendário (só motor Gemini). Sem {@code google_calendar_id}

 *     no tenant, o modo mock grava na mesma em {@code tenant_appointments}; integração Google exige o ID nas definições.

 * @param conversationId identificador da conversa para rastreio de agendamentos (pode ser null).

 * @param crmContext parágrafo opcional com dados do CRM para personalização (pode ser null).

 * @param schedulingEnforcedChoice data+hora fixadas pelo backend quando o cliente escolheu opção da lista (evita

 *     parâmetros errados vindos só do modelo).

 * @param schedulingBlockCreateAppointment quando verdadeiro, {@code create_appointment} deve recusar (cliente ainda não

 *     confirmou após escolher número da lista).

 * @param schedulingSlotAnchorDate data do último {@code [slot_date:…]} no histórico — validação contra o modelo.

 */

public record AICompletionRequest(

        TenantId tenantId,

        List<Message> conversationHistory,

        List<KnowledgeHit> knowledgeHits,

        String userMessage,

        String systemPrompt,

        AiChatProvider chatProvider,

        boolean resumeAfterHumanIntervention,

        boolean schedulingToolsEnabled,

        String conversationId,

        String crmContext,

        Optional<SchedulingEnforcedChoice> schedulingEnforcedChoice,

        boolean schedulingBlockCreateAppointment,

        Optional<LocalDate> schedulingSlotAnchorDate) {



    public AICompletionRequest(

            TenantId tenantId,

            List<Message> conversationHistory,

            List<KnowledgeHit> knowledgeHits,

            String userMessage,

            String systemPrompt,

            AiChatProvider chatProvider) {

        this(

                tenantId,

                conversationHistory,

                knowledgeHits,

                userMessage,

                systemPrompt,

                chatProvider,

                false,

                false,

                null,

                null,

                Optional.empty(),

                false,

                Optional.empty());

    }



    public AICompletionRequest(

            TenantId tenantId,

            List<Message> conversationHistory,

            List<KnowledgeHit> knowledgeHits,

            String userMessage,

            String systemPrompt,

            AiChatProvider chatProvider,

            boolean resumeAfterHumanIntervention) {

        this(

                tenantId,

                conversationHistory,

                knowledgeHits,

                userMessage,

                systemPrompt,

                chatProvider,

                resumeAfterHumanIntervention,

                false,

                null,

                null,

                Optional.empty(),

                false,

                Optional.empty());

    }



    public AICompletionRequest {

        if (tenantId == null) {

            throw new IllegalArgumentException("tenantId is required");

        }

        if (userMessage == null || userMessage.isBlank()) {

            throw new IllegalArgumentException("userMessage must not be blank");

        }

        if (systemPrompt == null) {

            systemPrompt = "";

        }

        if (chatProvider == null) {

            throw new IllegalArgumentException("chatProvider is required");

        }

        if (conversationHistory == null) {

            conversationHistory = List.of();

        }

        if (knowledgeHits == null) {

            knowledgeHits = List.of();

        }

        if (schedulingEnforcedChoice == null) {

            schedulingEnforcedChoice = Optional.empty();

        }

        if (schedulingSlotAnchorDate == null) {

            schedulingSlotAnchorDate = Optional.empty();

        }

    }

}

