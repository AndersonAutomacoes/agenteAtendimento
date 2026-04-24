package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.CollectionUtils;

/**
 * Monta o conteúdo do {@link org.springframework.ai.chat.messages.SystemMessage} com persona por tenant e RAG.
 */
public final class RagSystemPromptComposer {

    private static final String ADDITIONAL_INSTRUCTION =
            "Instrução Adicional: Use apenas o contexto fornecido para responder. Se não souber, diga que não "
                    + "possui essa informação.";

    private static final String HISTORY_COHERENCE =
            "Continuidade da conversa: seguem mensagens anteriores deste contacto (histórico recente). "
                    + "Usa esse histórico para manter coerência, evitar repetir o que já foi dito sem necessidade "
                    + "e responder em continuidade ao fluxo. Se algo no histórico conflitar com factos da base de "
                    + "conhecimento abaixo, privilegia a informação factual da base quando for aplicável.";

    private static final String RESUME_AFTER_HUMAN =
            "Você está retomando um atendimento que foi conduzido temporariamente por um humano. Analise as "
                    + "mensagens marcadas como atendente humano no histórico recente e siga as orientações ou "
                    + "acordos feitos por ele (como preços, descontos ou agendamentos).";

    private static final String PERSONA_ENFORCEMENT =
            "Regra prioritária: cumpra rigorosamente a Instrução de Personalidade deste tenant em tom, estilo e "
                    + "forma de responder. Só viole essa instrução quando houver conflito explícito com política de "
                    + "segurança, regras de ferramentas ou factos do contexto.";

    private static final String SCHEDULING_POLICY =
            "Agendamento: você tem permissão para agendar serviços. Separação estrita de ferramentas: "
                    + "Intenção CANCELAR (cancelar, desmarcar, anular agendamento): está PROIBIDO chamar check_availability "
                    + "(ferramenta que lista horários livres / disponibilidade). Nesse caso o único fluxo permitido é "
                    + "get_active_appointments e, quando aplicável, cancel_appointment — nunca simule ou invente um ID. "
                    + "Assim que o utilizador mencionar cancelar ou desmarcar, chame get_active_appointments neste turno e "
                    + "mostre a lista de agendamentos activos — não peça permissão para «ver a lista» ou «mostrar os "
                    + "compromissos»; apresente-os de imediato. "
                    + "(1) Se o utilizador pergunta «quais horários», «tem vaga», disponibilidade ou equivalente, "
                    + "chame APENAS check_availability com a data em yyyy-MM-DD — NUNCA chame create_appointment nesse turno. "
                    + "(2) Você está PROIBIDO de chamar create_appointment se o usuário enviar apenas um número. "
                    + "Quando o cliente responde só com um número (ex.: «14», «3», «opção 7»), o backend já respondeu diretamente "
                    + "com a confirmação — você NÃO precisa fazer nada nesse turno. "
                    + "(3) create_appointment só depois de o cliente confirmar com sim/ok. "
                    + "NUNCA diga que o agendamento está confirmado só em texto: só após create_appointment devolver "
                    + "mensagem começando por \"Agendamento criado\". "
                    + "Quando o utilizador indicar uma data concreta (ex.: 13/04 ou 13 de abril), use exactamente esse dia "
                    + "e mês ao chamar check_availability e create_appointment — não substitua por outro dia. "
                    + "Se não houver vaga nessa data, diga e ofereça outras opções; não agende noutro dia sem o cliente aceitar. "
                    + "Antes de check_availability, a data deve ser hoje ou futura no fuso do calendário; se o dia já "
                    + "passou, explique com cordialidade — sem mensagens técnicas nem prefixos tipo \"Erro:\". "
                    + "Nunca copie para o cliente texto interno de ferramentas, códigos ou avisos que pareçam log. "
                    + "Não reproduza na mensagem ao cliente instruções para o sistema (ex.: «Chame create_appointment», "
                    + "«check_availability», nomes de ferramentas em inglês, ou frases como «O cliente confirmou o "
                    + "agendamento» seguidas de parâmetros técnicos): fale só em linguagem natural com o cliente. "
                    + "Após check_availability com horários, o backend envia ao WhatsApp uma lista formatada (números/opções); "
                    + "você está PROIBIDO de repetir essa lista em texto na sua resposta. Seja sucinto: uma frase curta e "
                    + "cordial. Se check_availability indicar que não há vagas, confirme com cordialidade e ofereça outro dia. "
                    + "Se o histórico contiver [slot_date:yyyy-MM-DD] na última mensagem do assistente, use EXACTAMENTE essa "
                    + "data no parâmetro date de create_appointment — não use «hoje», amanhã inferido do relógio, nem outra "
                    + "data do contexto. "
                    + "Cancelamento: primeiro get_active_appointments. A lista devolvida pela ferramenta já mostra o ID da "
                    + "base (número antes do nome do serviço) — use esse número no texto ao cliente; não invente outro. "
                    + "Se o utilizador escolher um número ou «opção N» (mapeado na sessão), execute IMEDIATAMENTE "
                    + "cancel_appointment com esse valor, sem pedir mais confirmações nem repetir a lista. Quando o cliente "
                    + "disser o ID (ex.: «2») ou «opção 2», passe esse valor a cancel_appointment — o sistema resolve para a "
                    + "linha correcta. "
                    + "Não peça o telefone outra vez se o contacto da sessão WhatsApp já for conhecido — o parâmetro contact "
                    + "pode ser vazio ou o número do canal. Se cancel_appointment devolver erro, repita ao cliente o motivo real "
                    + "(ex.: ID não encontrado), sem pedir telefone em loop. "
                    + "Se perguntou se o cliente quer ver a lista de agendamentos e o cliente responde «sim», «sí», «ok» ou "
                    + "equivalente, invoque get_active_appointments nesse mesmo turno — não responda só com texto sem chamar a "
                    + "ferramenta. "
                    + "Reagendar / trocar horário: expressões como «trocar o horário», «mudar a marcação», «reagendar», "
                    + "«remarcar para amanhã» significam cancelar o compromisso existente e depois marcar outro — nunca "
                    + "apenas check_availability + create_appointment sem passar por get_active_appointments e "
                    + "cancel_appointment do agendamento correcto primeiro. Depois do cancelamento confirmado, siga o "
                    + "fluxo normal de disponibilidade e confirmação.";

    private RagSystemPromptComposer() {}

    /** Compatível com chamadas antigas (sem instrução de agendamento). */
    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention) {
        return compose(
                systemPrompt,
                knowledgeHits,
                hasPriorConversationTurns,
                resumeAfterHumanIntervention,
                false,
                null,
                null,
                null);
    }

    /** Motor OpenAI e chamadas sem ferramentas de calendário. */
    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention,
            String crmContext) {
        return compose(
                systemPrompt,
                knowledgeHits,
                hasPriorConversationTurns,
                resumeAfterHumanIntervention,
                false,
                null,
                null,
                crmContext);
    }

    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention,
            boolean schedulingToolsEnabled) {
        return compose(
                systemPrompt,
                knowledgeHits,
                hasPriorConversationTurns,
                resumeAfterHumanIntervention,
                schedulingToolsEnabled,
                null,
                null,
                null);
    }

    /**
     * @param schedulingTemporalContext parágrafo com data/hora de referência (ex.: {@link #schedulingTemporalAnchor})
     */
    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention,
            boolean schedulingToolsEnabled,
            String schedulingTemporalContext) {
        return compose(
                systemPrompt,
                knowledgeHits,
                hasPriorConversationTurns,
                resumeAfterHumanIntervention,
                schedulingToolsEnabled,
                null,
                schedulingTemporalContext,
                null);
    }

    /**
     * @param schedulingTemporalAttentionBanner linha curta no topo (ex.: {@link #schedulingTemporalAttentionBanner})
     * @param schedulingTemporalContext parágrafo com data/hora de referência (ex.: {@link #schedulingTemporalAnchor})
     * @param crmContext dados do cliente para personalização (pode ser null)
     */
    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention,
            boolean schedulingToolsEnabled,
            String schedulingTemporalAttentionBanner,
            String schedulingTemporalContext,
            String crmContext) {
        String personality = systemPrompt != null ? systemPrompt : "";
        StringBuilder sb = new StringBuilder();
        if (schedulingToolsEnabled
                && schedulingTemporalAttentionBanner != null
                && !schedulingTemporalAttentionBanner.isBlank()) {
            sb.append(schedulingTemporalAttentionBanner.strip()).append("\n\n");
        }
        sb.append(PERSONA_ENFORCEMENT).append("\n\n");
        sb.append("Instrução de Personalidade: ")
                .append(personality)
                .append(".\n\n");
        if (resumeAfterHumanIntervention) {
            sb.append(RESUME_AFTER_HUMAN).append("\n\n");
        }
        if (crmContext != null && !crmContext.isBlank()) {
            sb.append("Dados do cliente (CRM): ")
                    .append(crmContext.strip())
                    .append("\n\n");
        }
        if (schedulingToolsEnabled) {
            sb.append(SCHEDULING_POLICY).append("\n\n");
            if (schedulingTemporalContext != null && !schedulingTemporalContext.isBlank()) {
                sb.append(schedulingTemporalContext.strip()).append("\n\n");
            }
        }
        if (hasPriorConversationTurns) {
            sb.append(HISTORY_COHERENCE).append("\n\n");
        }
        sb.append("Contexto de Conhecimento: ")
                .append(formatKnowledge(knowledgeHits))
                .append("\n\n")
                .append(ADDITIONAL_INSTRUCTION);
        return sb.toString();
    }

    /**
     * Frase curta no início do system prompt (com ferramentas de calendário): hoje, dia da semana em português e
     * «amanhã» como data explícita no fuso do calendário.
     */
    public static String schedulingTemporalAttentionBanner(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        String weekday =
                capitalizeFirstAsciiLetter(today.format(DateTimeFormatter.ofPattern("EEEE", ptBr)));
        String currentDatePtBr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr));
        String tomorrowIso = tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String tomorrowPtBr = tomorrow.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr));
        return "Atenção: Hoje é "
                + currentDatePtBr
                + " ("
                + weekday
                + "). Qualquer menção a amanhã deve ser rigorosamente "
                + tomorrowIso
                + " ("
                + tomorrowPtBr
                + ").";
    }

    private static String capitalizeFirstAsciiLetter(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Data/hora atuais no fuso do calendário — o modelo deve usar isto como "hoje" e para o ano em datas sem ano.
     * <p>O valor de {@code current_date=} e de «Hoje é dd/MM/yyyy» é sempre {@link ZonedDateTime#now(ZoneId)} neste
     * fuso no momento do pedido (nunca uma data fixa em código); «amanhã» no prompt é explicitamente HOJE+1 com essa
     * mesma referência.
     */
    public static String schedulingTemporalAnchor(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        ZonedDateTime now = ZonedDateTime.now(zone);
        var today = now.toLocalDate();
        int year = today.getYear();
        String isoInstant =
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT));
        String hojeEh =
                today.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd", Locale.ENGLISH));
        String currentDateIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentDatePtBr =
                today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")));
        LocalDate tomorrow = today.plusDays(1);
        return "Obrigatório: Hoje é "
                + currentDatePtBr
                + " (current_date="
                + currentDateIso
                + "). Calcule datas relativas (amanhã, próxima quarta-feira, etc.) estritamente a partir desta data; "
                + "não use outro dia como «hoje» nem datas inferidas de contexto antigo.\n\n"
                + "HOJE_EH (fixo neste pedido; verdade absoluta): "
                + hojeEh
                + ". "
                + "Instrução crítica: se o utilizador pedir «amanhã», calcule matematicamente a data como HOJE + 1 dia no "
                + "mesmo fuso (ex.: se HOJE_EH termina em "
                + today
                + ", amanhã é "
                + tomorrow
                + " em yyyy-MM-DD). Proibido sugerir ou usar outra data para «amanhã» que não seja essa soma. "
                + "Referência temporal do sistema: fuso "
                + zone.getId()
                + "; agora é "
                + isoInstant
                + "; a data de hoje é "
                + today
                + " (yyyy-MM-DD). Quando o utilizador disser uma data sem ano (ex.: 13/04 ou 13/04 às 12:00), interprete "
                + "como dia/mês no calendário brasileiro (dd/MM) e utilize o ano "
                + year
                + ", salvo indicação explícita de outro ano. Não interprete como formato americano MM/dd. "
                + "Ao converter para yyyy-MM-DD, preserve o dia e o mês que o utilizador disse; não avance para o dia "
                + "seguinte nem use «amanhã» em substituição se o cliente pediu uma data concreta (ex.: 13/04). "
                + "Não diga que um dia ainda não ocorrido em relação a hoje já passou; se tiver dúvida, chame "
                + "check_availability com a data em yyyy-MM-DD antes de recusar. "
                + "Reformule sempre para o cliente em linguagem natural; não exponha mensagens de sistema.";
    }

    private static String formatKnowledge(List<KnowledgeHit> hits) {
        if (CollectionUtils.isEmpty(hits)) {
            return "(Nenhum trecho foi recuperado da base de conhecimento para esta pergunta.)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (KnowledgeHit h : hits) {
            sb.append("[").append(i++).append("] ").append(h.content()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
