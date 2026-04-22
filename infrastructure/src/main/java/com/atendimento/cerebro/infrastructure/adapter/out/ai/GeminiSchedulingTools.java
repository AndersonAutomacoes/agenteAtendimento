package com.atendimento.cerebro.infrastructure.adapter.out.ai;



import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;

import com.atendimento.cerebro.application.scheduling.AppointmentCalendarValidationResult;
import com.atendimento.cerebro.application.scheduling.CancelOptionMap;
import com.atendimento.cerebro.application.scheduling.AppointmentConfirmationDetails;
import com.atendimento.cerebro.application.scheduling.SchedulingCreateAppointmentResult;
import com.atendimento.cerebro.application.scheduling.SchedulingCalendarUserIntent;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.scheduling.SchedulingCancelSessionCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.application.scheduling.ToolCreateAppointmentPreparation;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.application.service.ChatService;

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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * para o modelo não inclui URL do Google Calendar (o cliente não deve receber esse link).
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

    private final AppointmentValidationService appointmentValidationService;

    private final AppointmentService appointmentService;

    private final ZoneId calendarZone;

    private final String latestUserMessage;

    /** Histórico recente + mensagem actual (para personalizar main_text com o serviço, ex.: Alinhamento 3D). */

    private final String transcriptForServiceHint;

    /** Definido no {@link com.atendimento.cerebro.application.dto.AICompletionRequest} (lista + slot_date); não usar ThreadLocal — as ferramentas podem correr noutra thread. */

    private final Optional<SchedulingEnforcedChoice> enforcedChoiceFromBackend;

    private final boolean blockCreateAppointment;

    /** Data do último {@code [slot_date:…]} no histórico — alinha {@code create_appointment} ao dia da lista. */
    private final Optional<LocalDate> schedulingSlotAnchorDate;



    /**

     * Contagem real de invocações aos métodos {@code @Tool} (não usar {@code ChatResponse#hasToolCalls()} — após o loop

     * interno a resposta final pode ser só texto e não refletir chamadas nas gerações).

     */

    private final AtomicInteger toolInvocationCount = new AtomicInteger(0);

    /** {@code true} após qualquer entrada em {@link #create_appointment} (modelo ou fallback no adaptador). */
    private final AtomicBoolean createAppointmentInvoked = new AtomicBoolean(false);

    /** Preenchido quando {@link #create_appointment} persiste com sucesso; consumido pelo motor Gemini para o card WhatsApp. */
    private final AtomicReference<AppointmentConfirmationDetails> successfulAppointmentDetails =
            new AtomicReference<>();



    public GeminiSchedulingTools(

            TenantId tenantId,

            String conversationId,

            AppointmentSchedulingPort scheduling,

            AppointmentValidationService appointmentValidationService,

            AppointmentService appointmentService,

            ZoneId calendarZone,

            String latestUserMessage,

            String transcriptForServiceHint,

            Optional<SchedulingEnforcedChoice> enforcedChoiceFromBackend,

            boolean blockCreateAppointment,

            Optional<LocalDate> schedulingSlotAnchorDate) {

        this.tenantId = tenantId;

        this.conversationId = conversationId;

        this.scheduling = scheduling;

        this.appointmentValidationService = Objects.requireNonNull(appointmentValidationService, "appointmentValidationService");

        this.appointmentService = Objects.requireNonNull(appointmentService, "appointmentService");

        this.calendarZone = calendarZone != null ? calendarZone : ZoneId.systemDefault();

        this.latestUserMessage = latestUserMessage != null ? latestUserMessage.strip() : "";

        this.transcriptForServiceHint = transcriptForServiceHint != null ? transcriptForServiceHint : "";

        this.enforcedChoiceFromBackend = enforcedChoiceFromBackend != null ? enforcedChoiceFromBackend : Optional.empty();

        this.blockCreateAppointment = blockCreateAppointment;

        this.schedulingSlotAnchorDate = schedulingSlotAnchorDate != null ? schedulingSlotAnchorDate : Optional.empty();

    }



    /** Quantas vezes o modelo disparou ferramentas de calendário neste pedido (soma de todas as tentativas com o mesmo objeto). */

    public int schedulingToolInvocationCount() {

        return toolInvocationCount.get();

    }

    /** Indica se {@link #create_appointment} foi invocado neste pedido (incluindo chamada programática do adaptador). */
    public boolean createAppointmentWasInvoked() {
        return createAppointmentInvoked.get();
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

        if (SchedulingUserReplyNormalizer.shouldRefuseAvailabilityBecauseCancelIntent(
                transcriptForServiceHint, latestUserMessage)) {
            return "Não chame check_availability quando o cliente quer cancelar ou desmarcar. Use get_active_appointments; "
                    + "não liste horários livres.";
        }

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
        createAppointmentInvoked.set(true);

        String cancelCreateBlob =
                transcriptForServiceHint.isBlank()
                        ? latestUserMessage
                        : transcriptForServiceHint + "\n" + latestUserMessage;
        if (!enforcedChoiceFromBackend.isPresent()
                && SchedulingUserReplyNormalizer.looksLikeCancellationIntent(cancelCreateBlob)) {
            return "Não chame create_appointment: a conversa indica cancelamento. Use get_active_appointments e, se aplicável, "
                    + "cancel_appointment.";
        }

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

        String transcriptBlob =
                transcriptForServiceHint.isBlank()
                        ? latestUserMessage
                        : transcriptForServiceHint + "\n" + latestUserMessage;
        ToolCreateAppointmentPreparation prep =
                appointmentValidationService.prepareGeminiToolCreateAppointment(
                        date,
                        time,
                        enforcedChoiceFromBackend,
                        schedulingSlotAnchorDate,
                        latestUserMessage,
                        transcriptBlob);
        if (!prep.ok()) {
            LOG.warn(
                    "create_appointment: validação pré-calendário falhou tenant={} msg={}",
                    tenantId.value(),
                    prep.messageForGemini());
            return prep.messageForGemini();
        }
        if (enforcedChoiceFromBackend.isPresent()) {
            SchedulingEnforcedChoice r = enforcedChoiceFromBackend.get();
            LOG.info(
                    "create_appointment: parâmetros fixados pelo backend (lista + slot_date) tenant={} date={} time={}",
                    tenantId.value(),
                    prep.dateIso(),
                    prep.timeHhMm());
        }
        AppointmentCalendarValidationResult validated =
                appointmentValidationService.validateIsoDateAndTimeForCalendar(
                        prep.dateIso(), prep.timeHhMm(), calendarZone);
        if (!validated.valid()) {
            return validated.userMessage();
        }

        LOG.info(

                "Ferramenta create_appointment tenant={} date={} time={} client={} service={}",

                tenantId.value(),

                prep.dateIso(),

                prep.timeHhMm(),

                client_name,

                service);

        String result =
                appointmentService.createAppointment(
                        tenantId, prep.dateIso(), prep.timeHhMm(), client_name, service, conversationId);
        if (SchedulingCreateAppointmentResult.isSuccess(result)) {
            try {
                successfulAppointmentDetails.set(
                        new AppointmentConfirmationDetails(
                                service != null ? service.strip() : "",
                                client_name != null ? client_name.strip() : "",
                                validated.day(),
                                prep.timeHhMm()));
            } catch (Exception e) {
                LOG.warn("Não foi possível guardar dados para o card de confirmação: {}", e.toString());
            }
        }
        return result;

    }

    @Tool(
            name = "get_active_appointments",
            description =
                    "Lista agendamentos com estado AGENDADO para o número/contacto desta conversa. Cada linha começa com "
                            + "o ID da base (PK) antes do serviço. Se houver mais de um, o assistente deve mostrar a lista "
                            + "ao cliente antes de cancelar. Chame antes de cancel_appointment quando o cliente quiser "
                            + "cancelar e ainda não houver id escolhido.")
    public String get_active_appointments() {
        toolInvocationCount.incrementAndGet();
        String listed = appointmentService.getActiveAppointments(tenantId, conversationId, calendarZone);
        Map<Integer, Long> optionMap = CancelOptionMap.parseLastFromText(listed);
        if (!optionMap.isEmpty()) {
            SchedulingCancelSessionCapture.setOptionToAppointmentId(optionMap);
            SchedulingCancelSessionCapture.setWaitingForCancellationChoice(true);
        } else {
            SchedulingCancelSessionCapture.setWaitingForCancellationChoice(false);
        }
        return listed;
    }

    @Tool(
            name = "cancel_appointment",
            description =
                    "Cancela o agendamento. Passe appointment_id = ID mostrado na lista (número antes do serviço), ou "
                            + "«opção N» quando o mapa da sessão mapeia N para um ID. O backend também resolve a partir do "
                            + "apêndice [cancel_option_map:…] no histórico. O parâmetro contact é opcional no WhatsApp.")
    public String cancel_appointment(
            @ToolParam(
                            description =
                                    "Opcional nesta sessão se appointment_id vier da lista; telefone do WhatsApp ou e-mail "
                                            + "do CRM. Pode ser vazio.")
                    String contact,
            @ToolParam(
                            description =
                                    "ID numérico da lista (PK antes do serviço), ou «opção N», ou valor resolvido pelo mapa — "
                                            + "contra get_active_appointments nesta conversa.")
                    String appointment_id) {
        toolInvocationCount.incrementAndGet();
        String c = contact == null ? "" : contact;
        String blobForCancel =
                transcriptForServiceHint.isBlank()
                        ? latestUserMessage
                        : transcriptForServiceHint + "\n" + latestUserMessage;
        String resolved = CancelOptionMap.resolveAppointmentIdForCancel(appointment_id, blobForCancel);
        String result = appointmentService.cancelAppointment(tenantId, conversationId, c, resolved, calendarZone);
        ChatService.clearCancellationContext(conversationId);
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

