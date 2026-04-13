package com.atendimento.cerebro.infrastructure.adapter.out.ai;



import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;

import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationDetails;
import com.atendimento.cerebro.application.scheduling.SchedulingCreateAppointmentResult;
import com.atendimento.cerebro.application.scheduling.SchedulingCalendarUserIntent;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;

import com.atendimento.cerebro.domain.tenant.TenantId;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ArrayNode;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;

import java.time.ZoneId;

import java.time.format.DateTimeFormatter;

import java.time.format.DateTimeParseException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import java.util.regex.Pattern;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;

import org.springframework.ai.tool.annotation.ToolParam;



/**
 * Ferramentas por pedido: o {@link TenantId} e a conversa são fixados no construtor (não vêm do modelo).
 *
 * <p>O agendamento usa {@link AppointmentSchedulingPort}: com {@code cerebro.google.calendar.mock=true} usa o mock
 * local; com {@code mock=false} a implementação é {@code GoogleCalendarAppointmentSchedulingService}, que chama
 * {@link com.atendimento.cerebro.infrastructure.calendar.GoogleCalendarService} (escopo {@code calendar.events},
 * fuso {@link com.atendimento.cerebro.infrastructure.calendar.GoogleCalendarService#CALENDAR_ZONE}). O texto de sucesso
 * inclui o {@code htmlLink} do evento para o agente confirmar ao cliente.
 */
public class GeminiSchedulingTools {



    private static final Logger LOG = LoggerFactory.getLogger(GeminiSchedulingTools.class);

    private static final ObjectMapper JSON = new ObjectMapper();



    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;



    /** Perguntas só sobre listagem de horários — não criar agendamento neste turno. */

    private static final Pattern AVAILABILITY_QUESTION =

            Pattern.compile(

                    "(?is).*(quais|qual|que)\\s+(são\\s+)?(os\\s+)?(horários|horas?).*"

                            + "|(?is).*(mostrar?|liste|ver|exibir)\\s+(os\\s+)?(horários|horas|horário).*"

                            + "|(?is).*(tem\\s+vaga|há\\s+vaga)\\b.*");



    private final TenantId tenantId;

    private final String conversationId;

    private final AppointmentSchedulingPort scheduling;

    private final ZoneId calendarZone;

    private final String latestUserMessage;

    /** Histórico recente + mensagem actual (para personalizar main_text com o serviço, ex.: Alinhamento 3D). */

    private final String transcriptForServiceHint;

    /** Definido no {@link com.atendimento.cerebro.application.dto.AICompletionRequest} (lista + slot_date); não usar ThreadLocal — as ferramentas podem correr noutra thread. */

    private final Optional<SchedulingEnforcedChoice> enforcedChoiceFromBackend;

    private final boolean blockCreateAppointment;



    /**

     * Contagem real de invocações aos métodos {@code @Tool} (não usar {@code ChatResponse#hasToolCalls()} — após o loop

     * interno a resposta final pode ser só texto e não refletir chamadas nas gerações).

     */

    private final AtomicInteger toolInvocationCount = new AtomicInteger(0);

    /** Preenchido quando {@link #create_appointment} persiste com sucesso; consumido pelo motor Gemini para o card WhatsApp. */
    private final AtomicReference<AppointmentConfirmationDetails> successfulAppointmentDetails =
            new AtomicReference<>();



    public GeminiSchedulingTools(

            TenantId tenantId,

            String conversationId,

            AppointmentSchedulingPort scheduling,

            ZoneId calendarZone,

            String latestUserMessage,

            String transcriptForServiceHint,

            Optional<SchedulingEnforcedChoice> enforcedChoiceFromBackend,

            boolean blockCreateAppointment) {

        this.tenantId = tenantId;

        this.conversationId = conversationId;

        this.scheduling = scheduling;

        this.calendarZone = calendarZone != null ? calendarZone : ZoneId.systemDefault();

        this.latestUserMessage = latestUserMessage != null ? latestUserMessage.strip() : "";

        this.transcriptForServiceHint = transcriptForServiceHint != null ? transcriptForServiceHint : "";

        this.enforcedChoiceFromBackend = enforcedChoiceFromBackend != null ? enforcedChoiceFromBackend : Optional.empty();

        this.blockCreateAppointment = blockCreateAppointment;

    }



    /** Quantas vezes o modelo disparou ferramentas de calendário neste pedido (soma de todas as tentativas com o mesmo objeto). */

    public int schedulingToolInvocationCount() {

        return toolInvocationCount.get();

    }

    /**
     * Retorna e limpa os dados do último agendamento criado com sucesso neste pedido (para anexar o card ao texto
     * enviado ao cliente).
     */
    public Optional<AppointmentConfirmationDetails> takeSuccessfulAppointmentDetails() {
        return Optional.ofNullable(successfulAppointmentDetails.getAndSet(null));
    }



    @Tool(

            name = "check_availability",

            description =

                    "OBRIGATÓRIO antes de mostrar horários. Obtém horários livres para uma data (yyyy-MM-DD) e devolve um "

                    + "JSON estruturado para o WhatsApp (main_text + lista). NÃO escreva lista de horários em "

                    + "texto na sua resposta ao cliente — o backend envia a lista formatada (ou botões se estiverem activos). "

                            + "Use o ano da referência temporal do system prompt quando o utilizador disser só dia/mês (dd/MM). "

                            + "Se o utilizador perguntar «quais horários» ou equivalente, chame APENAS esta ferramenta (não "

                            + "create_appointment). Chame create_appointment só noutro turno, depois do cliente escolher e "

                            + "confirmar um horário concreto.")

    public String check_availability(@ToolParam(description = "Data no formato yyyy-MM-DD") String date) {

        toolInvocationCount.incrementAndGet();

        LOG.info("Ferramenta check_availability tenant={} date={}", tenantId.value(), date);



        LocalDate day;

        try {

            day = LocalDate.parse(date.strip(), ISO_DATE);

        } catch (DateTimeParseException e) {

            return "Peça ao cliente uma data válida (dia, mês e ano) de forma clara, sem mencionar formatos técnicos "

                    + "nem códigos de erro ao utilizador.";

        }

        String blobForIntent =
                transcriptForServiceHint.isBlank()
                        ? latestUserMessage
                        : transcriptForServiceHint + "\n" + latestUserMessage;
        Optional<LocalDate> wantTomorrow =
                SchedulingCalendarUserIntent.expectedDayIfTomorrowMentioned(blobForIntent, calendarZone);
        if (wantTomorrow.isPresent() && !wantTomorrow.get().equals(day)) {
            LOG.warn(
                    "check_availability: 'amanhã' alinhado de {} para {} (tenant={})",
                    day,
                    wantTomorrow.get(),
                    tenantId.value());
            day = wantTomorrow.get();
        }
        int refYear = LocalDate.now(calendarZone).getYear();
        Optional<LocalDate> brInLatest =
                SchedulingCalendarUserIntent.lastBrazilianDateInText(latestUserMessage, refYear);
        if (wantTomorrow.isEmpty()
                && brInLatest.isPresent()
                && !brInLatest.get().equals(day)) {
            LOG.warn(
                    "check_availability: data alinhada ao dd/MM do utilizador de {} para {} (tenant={})",
                    day,
                    brInLatest.get(),
                    tenantId.value());
            day = brInLatest.get();
        }
        String isoDate = day.format(ISO_DATE);

        LocalDate today = LocalDate.now(calendarZone);

        if (day.isBefore(today)) {

            return "Desculpe, essa data já passou. Poderia escolher uma data a partir de amanhã?";

        }

        String calendarLine = scheduling.checkAvailability(tenantId, isoDate);
        if (!SchedulingCalendarUserIntent.availabilityLineMatchesRequestedDate(calendarLine, day)) {
            LOG.warn(
                    "check_availability: linha do calendário sem a data pedida {} (tenant={})",
                    isoDate,
                    tenantId.value());
            return "Não foi possível confirmar os horários para a data "
                    + isoDate
                    + " neste momento. Diga ao cliente com cordialidade que pode haver uma inconsistência e pergunta se "
                    + "deseja tentar outro dia ou repetir o pedido.";
        }

        List<String> times = SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(calendarLine);

        times = SchedulingSlotCapture.normalizeSlotTimes(times);

        if (times.isEmpty()) {

            return SchedulingSlotCapture.SLOTS_ALL_OCCUPIED_PT;

        }



        String hintBlob =

                transcriptForServiceHint.isBlank()

                        ? latestUserMessage

                        : transcriptForServiceHint + "\n" + latestUserMessage;

        String mainText = SchedulingSlotCapture.buildWhatsAppMainText(day, calendarZone, hintBlob);

        SchedulingSlotCapture.setStructuredAvailability(mainText, times, day);



        try {

            return buildEvolutionAvailabilityJson(mainText, times);

        } catch (Exception e) {

            LOG.warn("Falha ao serializar JSON de disponibilidade (tenant={}): {}", tenantId.value(), e.toString());

            return calendarLine;

        }

    }



    /**

     * Payload esperado pela integração Evolution (sendButtons é montado no outbound a partir de {@link

     * com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply}); este JSON é o retorno explícito da ferramenta

     * para o modelo e para observabilidade.

     */

    static String buildEvolutionAvailabilityJson(String mainText, List<String> times) throws Exception {

        List<String> clean = SchedulingSlotCapture.normalizeSlotTimes(times);

        ObjectNode root = JSON.createObjectNode();

        root.put("main_text", mainText != null ? mainText : "");

        ArrayNode buttons = root.putArray("buttons");

        for (String t : clean) {

            ObjectNode b = buttons.addObject();

            b.put("id", t);

            b.put("label", t);

        }

        return JSON.writeValueAsString(root);

    }



    @Tool(

            name = "create_appointment",

            description =

                    "Cria o evento no calendário APÓS o utilizador ter escolhido e confirmado um horário que apareceu em "

                            + "check_availability (mesma data em yyyy-MM-DD). PROIBIDO: chamar se o utilizador só pediu "

                            + "horários/disponibilidade neste turno; PROIBIDO inventar ou supor um horário que o cliente "

                            + "não escolheu. O utilizador deve ter indicado explicitamente o horário (texto ou botão).")

    public String create_appointment(

            @ToolParam(description = "Data yyyy-MM-DD (deve ser a mesma data acordada após check_availability)") String date,

            @ToolParam(description = "Hora local HH:mm (24h), a que o cliente confirmou)") String time,

            @ToolParam(description = "Nome do cliente") String client_name,

            @ToolParam(description = "Nome do serviço") String service) {

        toolInvocationCount.incrementAndGet();

        if (blockCreateAppointment) {

            return "Não chame create_appointment neste turno: o cliente escolheu um número da lista mas ainda não confirmou. "

                    + "Pergunte «Posso confirmar?» e só depois da confirmação (sim) poderá criar.";

        }

        if (isAvailabilityListingQuery(latestUserMessage)) {

            LOG.warn(

                    "create_appointment recusado: mensagem parece só pedido de horários (tenant={})",

                    tenantId.value());

            return "Não é possível criar agendamento: o utilizador apenas pediu horários ou disponibilidade. "

                    + "Não chame create_appointment; use só check_availability até o cliente escolher um horário concreto.";

        }

        if (enforcedChoiceFromBackend.isPresent()) {
            SchedulingEnforcedChoice r = enforcedChoiceFromBackend.get();
            date = r.date().format(ISO_DATE);
            time = r.timeHhMm();
            LOG.info(
                    "create_appointment: parâmetros fixados pelo backend (lista + slot_date) tenant={} date={} time={}",
                    tenantId.value(),
                    date,
                    time);
        }

        LOG.info(

                "Ferramenta create_appointment tenant={} date={} time={} client={} service={}",

                tenantId.value(),

                date,

                time,

                client_name,

                service);

        String result = scheduling.createAppointment(tenantId, date, time, client_name, service, conversationId);
        if (SchedulingCreateAppointmentResult.isSuccess(result)) {
            try {
                LocalDate day = LocalDate.parse(date.strip(), ISO_DATE);
                successfulAppointmentDetails.set(
                        new AppointmentConfirmationDetails(
                                service != null ? service.strip() : "",
                                client_name != null ? client_name.strip() : "",
                                day,
                                time != null ? time.strip() : ""));
            } catch (Exception e) {
                LOG.warn("Não foi possível guardar dados para o card de confirmação: {}", e.toString());
            }
        }
        return result;

    }



    private static boolean isAvailabilityListingQuery(String raw) {

        if (raw == null || raw.isBlank()) {

            return false;

        }

        String u = raw.toLowerCase(Locale.ROOT).strip();

        if (AVAILABILITY_QUESTION.matcher(u).matches()) {

            return true;

        }

        return u.matches("(?is)^\\s*(horários?|horas?)\\??\\s*$");

    }

}

