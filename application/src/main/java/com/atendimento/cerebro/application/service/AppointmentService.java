package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.scheduling.CancelOptionMap;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listagem e cancelamento de agendamentos: valida contacto contra a sessão, remove evento no calendário e grava
 * {@code booking_status = CANCELADO}.
 */
public class AppointmentService {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentService.class);

    /**
     * Prefixo após {@code deleteCalendarEvent} confirmado no Google Calendar e {@code markCancelled} na base — só então o
     * cliente deve ver esta frase.
     */
    public static final String CANCELLATION_SUCCESS_MESSAGE_PREFIX = "O horário foi liberado com sucesso.";

    /** Quando o registo já estava CANCELADO na base (não reexecuta delete no Google). Não implica remoção acabada de ocorrer. */
    public static final String CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX =
            "Este agendamento já constava como cancelado no sistema.";

    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TenantAppointmentQueryPort appointmentQuery;
    private final TenantAppointmentStorePort appointmentStore;
    private final AppointmentSchedulingPort scheduling;
    private final CrmCustomerQueryPort crmCustomerQuery;

    public AppointmentService(
            TenantAppointmentQueryPort appointmentQuery,
            TenantAppointmentStorePort appointmentStore,
            AppointmentSchedulingPort scheduling,
            CrmCustomerQueryPort crmCustomerQuery) {
        this.appointmentQuery = appointmentQuery;
        this.appointmentStore = appointmentStore;
        this.scheduling = scheduling;
        this.crmCustomerQuery = crmCustomerQuery;
    }

    /**
     * Indica resposta de cancelamento tratado (sucesso após Google + base, ou idempotência já cancelado). Usado para
     * limpar contexto de fluxo.
     */
    public static boolean isSuccessfulCancellationReply(String message) {
        if (message == null) {
            return false;
        }
        String s = message.strip();
        return s.startsWith(CANCELLATION_SUCCESS_MESSAGE_PREFIX)
                || s.startsWith(CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX);
    }

    /**
     * Lista agendamentos com estado AGENDADO para a conversa actual (número WhatsApp / sessão).
     *
     * <p>Cada linha usa o {@code id} (PK) da base como prefixo — o mesmo valor que {@link #cancelAppointment} espera.
     *
     * @return texto para o modelo, com instrução de listar vários itens se houver mais de um
     */
    public String getActiveAppointments(TenantId tenantId, String conversationId, ZoneId calendarZone) {
        if (conversationId == null || conversationId.isBlank()) {
            return "Não foi possível listar agendamentos: sessão de conversa inválida.";
        }
        String conv = conversationId.strip();
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        List<TenantAppointmentListItem> rows =
                appointmentQuery.listAgendadoByConversationOrderedAscending(tenantId, conv, zoneId);
        if (rows.isEmpty()) {
            return "Não há agendamentos com estado AGENDADO para o número desta conversa.";
        }
        Map<Integer, Long> optionToAppointmentId = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        if (rows.size() > 1) {
            sb.append("Existem vários agendamentos activos. Pergunte qual deseja cancelar.\n\n");
        }
        sb.append("Agendamentos AGENDADO:\n");
        for (TenantAppointmentListItem r : rows) {
            String when = SLOT_FMT.format(r.startsAt().atZone(z));
            int shownId = Math.toIntExact(r.id());
            optionToAppointmentId.put(shownId, r.id());
            sb.append(shownId)
                    .append(") ")
                    .append(r.serviceName() != null ? r.serviceName().strip() : "")
                    .append(" — ")
                    .append(when)
                    .append("\n");
        }
        sb.append(CancelOptionMap.buildAppendix(optionToAppointmentId));
        return sb.toString();
    }

    /**
     * Cancela por {@code appointmentId} após validação de contacto; actualiza {@code booking_status} para CANCELADO.
     *
     * <p>O ID deve corresponder ao número (PK) mostrado antes de cada linha em {@link #getActiveAppointments}, ou ser
     * resolvido via {@link com.atendimento.cerebro.application.scheduling.CancelOptionMap} («opção N» e mapa na sessão).
     * Não infira ID a partir de datas ou ordem sem o cliente/ modelo terem escolhido a linha correspondente.
     *
     * @param appointmentIdRaw identificador numérico vindo da lista (não texto livre)
     */
    public String cancelAppointment(
            TenantId tenantId, String conversationId, String contact, String appointmentIdRaw, ZoneId calendarZone) {
        if (conversationId == null || conversationId.isBlank()) {
            return "Não foi possível cancelar: sessão de conversa inválida. Peça ao cliente para tentar de novo pelo mesmo canal.";
        }
        String conv = conversationId.strip();
        if (appointmentIdRaw == null || appointmentIdRaw.isBlank()) {
            return "Não foi possível identificar qual agendamento cancelar. Liste primeiro os agendamentos activos e "
                    + "pergunte ao cliente qual ID deseja cancelar (número antes do serviço na lista).";
        }
        String rawId = appointmentIdRaw.strip();
        if (!rawId.matches("^\\d+$")) {
            return "Peça ao cliente o ID mostrado na lista (número antes do serviço) ou confirme o horário a cancelar.";
        }
        long appointmentId;
        try {
            appointmentId = Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            return "Use apenas o ID numérico mostrado na lista (antes do nome do serviço).";
        }
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();

        Optional<TenantAppointmentListItem> any =
                appointmentQuery.findByIdForTenantAndConversation(tenantId, appointmentId, conv, zoneId);
        if (any.isEmpty()) {
            LOG.error(
                    "cancelAppointment: appointment_id={} não encontrado para tenant={} conversationId={}",
                    appointmentId,
                    tenantId.value(),
                    conv);
            return "Não encontrámos esse agendamento para si. Peça ao cliente para confirmar o ID na última lista "
                    + "(número antes do serviço) ou peça novamente a lista de agendamentos activos antes de cancelar.";
        }
        TenantAppointmentListItem row = any.get();
        if (row.bookingStatus() == TenantAppointmentListItem.BookingStatus.CANCELADO) {
            return alreadyCancelledConfirmation(row, z);
        }

        warnIfContactSanityMismatch(tenantId, conv, contact);

        String googleEventId = row.googleEventId();
        if (googleEventId == null || googleEventId.isBlank()) {
            LOG.error(
                    "cancelAppointment: google_event_id em falta — impossível remover evento no calendário "
                            + "(appointmentId={} tenant={} conversationId={})",
                    row.id(),
                    tenantId.value(),
                    conv);
            return "Não foi possível concluir o cancelamento neste momento por um problema técnico na agenda. "
                    + "Não confirme ao cliente que já foi cancelado; peça ao suporte ou tente mais tarde.";
        }
        String gid = googleEventId.strip();

        LOG.info(
                "cancelAppointment: a remover evento no Google Calendar (tenant={} appointmentId={} googleEventId={})",
                tenantId.value(),
                row.id(),
                gid);

        boolean removedFromCalendar;
        try {
            removedFromCalendar = scheduling.deleteCalendarEvent(tenantId, gid);
        } catch (RuntimeException e) {
            LOG.error(
                    "cancelAppointment: excepção ao remover evento do calendário (appointmentId={} tenant={} googleEventId={})",
                    row.id(),
                    tenantId.value(),
                    gid,
                    e);
            return "Não foi possível remover o evento do calendário neste momento. Não confirme sucesso ao cliente; "
                    + "peça para tentar de novo ou contacte o suporte.";
        }
        if (!removedFromCalendar) {
            LOG.error(
                    "cancelAppointment: deleteCalendarEvent retornou false — calendário não sincronizado "
                            + "(appointmentId={} tenant={} googleEventId={})",
                    row.id(),
                    tenantId.value(),
                    gid);
            return "Não foi possível remover o evento no Google Calendar (calendário indisponível ou não configurado). "
                    + "O cancelamento não foi concluído — não diga ao cliente que o horário já foi libertado.";
        }
        LOG.info(
                "cancelAppointment: deleteCalendarEvent concluído com sucesso — a prosseguir para gravar CANCELADO na base "
                        + "(tenant={} appointmentId={} googleEventId={})",
                tenantId.value(),
                row.id(),
                gid);

        if (!appointmentStore.markCancelled(row.id(), Instant.now())) {
            LOG.error(
                    "cancelAppointment: markCancelled não actualizou nenhuma linha AGENDADO (appointmentId={} tenant={})",
                    row.id(),
                    tenantId.value());
            return "Não foi possível gravar o cancelamento na base de dados (o estado pode ter mudado). Não confirme "
                    + "sucesso ao cliente; peça para tentar de novo.";
        }
        return slotReleasedConfirmation(row, z);
    }

    private static String slotReleasedConfirmation(TenantAppointmentListItem row, ZoneId z) {
        LocalDate day = row.startsAt().atZone(z).toLocalDate();
        String dateStr = DAY_FMT.format(day);
        String svc = row.serviceName() != null ? row.serviceName().strip() : "";
        return CANCELLATION_SUCCESS_MESSAGE_PREFIX
                + " Serviço: "
                + svc
                + ". Data: "
                + dateStr
                + ". A vaga já está disponível para outros clientes.";
    }

    private static String alreadyCancelledConfirmation(TenantAppointmentListItem row, ZoneId z) {
        LocalDate day = row.startsAt().atZone(z).toLocalDate();
        String dateStr = DAY_FMT.format(day);
        String svc = row.serviceName() != null ? row.serviceName().strip() : "";
        return CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX + " Serviço: " + svc + ". Data: " + dateStr + ".";
    }

    /**
     * Verificação opcional (não bloqueia cancelamento quando o appointmentId já foi validado na conversa). Usada para
     * métricas / diagnóstico se o modelo enviar um contacto inconsistente.
     */
    void warnIfContactSanityMismatch(TenantId tenantId, String conversationId, String contactRaw) {
        String c = contactRaw == null ? "" : contactRaw.strip();
        if (c.isEmpty()) {
            return;
        }
        if (!contactMatchesSessionFlexible(tenantId, conversationId, c)) {
            LOG.error(
                    "cancelAppointment: validação de contacto falhou após normalização (tenant={} conversationId={})",
                    tenantId.value(),
                    conversationId);
        }
    }

    /**
     * E-mail: comparação sem espaços e case-insensitive. Telefone: só dígitos, alinhando prefixo internacional 55 com
     * número local.
     */
    boolean contactMatchesSessionFlexible(TenantId tenantId, String conversationId, String contactRaw) {
        String c = contactRaw == null ? "" : contactRaw.strip();
        if (c.isEmpty()) {
            return false;
        }
        if (c.indexOf('@') >= 0) {
            Optional<CrmCustomerRecord> crm = crmCustomerQuery.findByTenantAndConversationId(tenantId, conversationId);
            if (crm.isEmpty()) {
                return false;
            }
            String em = crm.get().email();
            if (em == null || em.isBlank()) {
                return false;
            }
            return normalizeEmailForComparison(em).equals(normalizeEmailForComparison(c));
        }
        Optional<String> waDigits = CrmConversationSupport.phoneDigitsOnlyFromConversationId(conversationId);
        if (waDigits.isEmpty()) {
            return false;
        }
        String a = normalizePhoneDigitsForComparison(waDigits.get());
        String b = normalizePhoneDigitsForComparison(c);
        return !a.isEmpty() && !b.isEmpty() && a.equals(b);
    }

    static String normalizeEmailForComparison(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Remove não-dígitos e harmoniza Brasil: com ou sem prefixo {@code 55} (ex.: sessão {@code wa-5511…} vs contacto
     * {@code (11) 9…}).
     */
    static String normalizePhoneDigitsForComparison(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("55") && d.length() >= 12) {
            d = d.substring(2);
        }
        return d;
    }
}
