package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.logging.AppointmentAuditLog;
import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.event.AppointmentCancelledEvent;
import com.atendimento.cerebro.application.event.AppointmentConfirmedEvent;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.PlanLimitPolicyPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.TenantServiceCatalogPort;
import com.atendimento.cerebro.application.scheduling.SchedulingExplicitTimeShortcut;
import com.atendimento.cerebro.application.scheduling.CancelOptionMap;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.application.scheduling.ReagendamentoDeParaHint;
import com.atendimento.cerebro.application.scheduling.SchedulingUserReplyNormalizer;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

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
    public static final String NO_ACTIVE_APPOINTMENTS_FRIENDLY_MESSAGE =
            "Sem agendamentos ativos no momento. Posso verificar horários para um novo agendamento "
                    + "ou ajudar com outra dúvida?";
    public static final String LIST_INVALID_SESSION_FRIENDLY_MESSAGE =
            "Tive um problema ao identificar esta conversa agora. Pode enviar novamente a sua mensagem? "
                    + "Se preferir, já posso ajudar com um novo agendamento.";
    public static final String CANCEL_INVALID_SESSION_FRIENDLY_MESSAGE =
            "Não consegui confirmar esta conversa para cancelar o agendamento agora. Pode tentar novamente "
                    + "nesta mesma conversa?";
    public static final String CANCEL_TECHNICAL_ISSUE_FRIENDLY_MESSAGE =
            "Tive um problema técnico para concluir o cancelamento agora. O agendamento anterior pode ainda "
                    + "estar ativo. Pode tentar novamente em instantes?";
    public static final String CANCEL_CALENDAR_REMOVE_FAILED_FRIENDLY_MESSAGE =
            "Tive um problema técnico na agenda e não consegui remover o agendamento agora. O horário anterior "
                    + "ainda pode estar ativo. Pode tentar novamente em instantes?";
    public static final String CANCEL_PERSISTENCE_FAILED_FRIENDLY_MESSAGE =
            "Consegui iniciar o cancelamento, mas houve falha ao atualizar o sistema. Para sua segurança, "
                    + "considere que o horário anterior pode ainda estar ativo e tente novamente em instantes.";
    public static final String CANCEL_LIST_UNAVAILABLE_FRIENDLY_MESSAGE =
            "Tive um problema técnico para listar os agendamentos agora. Pode tentar novamente em instantes?";

    /** Texto final do card de listagem em {@link #getActiveAppointments} (WhatsApp). */
    public static final String LIST_APPOINTMENTS_CANCEL_HINT_FOOTER_PT =
            "Para *Cancelar*, diga apenas o código do agendamento.";
    public static final String LIST_APPOINTMENTS_RESCHEDULE_HINT_FOOTER_PT =
            "Para *Reagendar*, diga o código do agendamento (número à esquerda) e depois informe a nova data e horário."
                    + "\nPara *Cancelar*, diga apenas o código do agendamento.";

    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_SERVICES_IN_REPLY = 10;

    private final TenantAppointmentQueryPort appointmentQuery;
    private final TenantAppointmentStorePort appointmentStore;
    private final AppointmentSchedulingPort scheduling;
    private final TenantServiceCatalogPort tenantServiceCatalog;
    private final CrmCustomerQueryPort crmCustomerQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final PlanLimitPolicyPort planLimitPolicy;

    public AppointmentService(
            TenantAppointmentQueryPort appointmentQuery,
            TenantAppointmentStorePort appointmentStore,
            AppointmentSchedulingPort scheduling,
            TenantServiceCatalogPort tenantServiceCatalog,
            CrmCustomerQueryPort crmCustomerQuery,
            ApplicationEventPublisher eventPublisher) {
        this(
                appointmentQuery,
                appointmentStore,
                scheduling,
                tenantServiceCatalog,
                crmCustomerQuery,
                eventPublisher,
                null,
                null);
    }

    public AppointmentService(
            TenantAppointmentQueryPort appointmentQuery,
            TenantAppointmentStorePort appointmentStore,
            AppointmentSchedulingPort scheduling,
            TenantServiceCatalogPort tenantServiceCatalog,
            CrmCustomerQueryPort crmCustomerQuery,
            ApplicationEventPublisher eventPublisher,
            TenantConfigurationStorePort tenantConfigurationStore,
            PlanLimitPolicyPort planLimitPolicy) {
        this.appointmentQuery = appointmentQuery;
        this.appointmentStore = appointmentStore;
        this.scheduling = scheduling;
        this.tenantServiceCatalog = tenantServiceCatalog;
        this.crmCustomerQuery = crmCustomerQuery;
        this.eventPublisher = eventPublisher;
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.planLimitPolicy = planLimitPolicy;
    }

    /**
     * Cria agendamento no calendário e na base via {@link AppointmentSchedulingPort}; após sucesso tenta notificar o
     * cliente por WhatsApp (falha de notificação não reverte o agendamento).
     */
    /**
     * Cria o agendamento; devolve mensagem e id na base (quando existir), após o fluxo de evento/auditoria.
     */
    public CreateAppointmentResult createAppointmentWithResult(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId,
            ZoneId calendarZone) {
        Optional<CreateAppointmentResult> limitFailure =
                validateMonthlyPlanLimit(tenantId, isoDate, calendarZone);
        if (limitFailure.isPresent()) {
            return limitFailure.get();
        }
        var result =
                tenantServiceCatalog
                        .findServiceIdByName(tenantId, serviceName)
                        .map(
                                serviceId ->
                scheduling.createAppointment(
                        tenantId, isoDate, localTime, clientName, serviceId, serviceName, conversationId))
                        .orElseGet(
                                () ->
                                        CreateAppointmentResult.failure(
                                                buildUnsupportedServiceMessage(tenantId, serviceName)));
        if (result.isSuccess() && result.appointmentDatabaseId() != null) {
            try {
                appointmentStore.markConfirmationNotificationPending(result.appointmentDatabaseId());
                ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
                LocalDate day = LocalDate.parse(isoDate.strip(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalTime time = parseAppointmentLocalTime(localTime);
                Instant startsAt = LocalDateTime.of(day, time).atZone(z).toInstant();
                String phone =
                        CrmConversationSupport.phoneDigitsOnlyFromConversationId(conversationId)
                                .orElse("");
                String slotForAudit =
                        SLOT_FMT.format(java.time.ZonedDateTime.of(day, time, z));
                AppointmentAuditLog.appConfirmed(
                        String.valueOf(result.appointmentDatabaseId()), phone, slotForAudit);
                eventPublisher.publishEvent(
                        new AppointmentConfirmedEvent(
                                tenantId,
                                result.appointmentDatabaseId(),
                                phone,
                                clientName != null ? clientName.strip() : "",
                                serviceName != null ? serviceName.strip() : "",
                                startsAt,
                                z.getId()));
            } catch (DateTimeParseException e) {
                LOG.error(
                        "createAppointment: evento omitido — isoDate inválido após sucesso do scheduling tenant={} isoDate={}",
                        tenantId.value(),
                        isoDate,
                        e);
                AppointmentAuditLog.appError(
                        "Validation", "Invalid isoDate after scheduling success: " + e.getMessage());
            } catch (RuntimeException e) {
                LOG.error(
                        "createAppointment: falha ao publicar AppointmentConfirmedEvent tenant={} conversationId={}",
                        tenantId.value(),
                        conversationId,
                        e);
                AppointmentAuditLog.appError(
                        "EventPublish", "AppointmentConfirmedEvent: " + e.getMessage());
            }
        }
        return result;
    }

    private Optional<CreateAppointmentResult> validateMonthlyPlanLimit(
            TenantId tenantId, String isoDate, ZoneId calendarZone) {
        if (tenantConfigurationStore == null || planLimitPolicy == null) {
            return Optional.empty();
        }
        ProfileLevel level =
                tenantConfigurationStore
                        .findByTenantId(tenantId)
                        .map(c -> c.profileLevel())
                        .orElse(ProfileLevel.BASIC);
        Optional<Integer> maxOpt = planLimitPolicy.maxAppointmentsPerMonth(level);
        if (maxOpt.isEmpty()) {
            return Optional.empty();
        }
        int max = maxOpt.get();
        if (max <= 0) {
            return Optional.empty();
        }
        ZoneId zone = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        LocalDate day;
        try {
            day = LocalDate.parse(isoDate.strip(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        LocalDate monthStart = day.withDayOfMonth(1);
        Instant from = monthStart.atStartOfDay(zone).toInstant();
        Instant to = monthStart.plusMonths(1).atStartOfDay(zone).toInstant();
        long current = appointmentQuery.countStartsInRange(tenantId, from, to);
        if (current < max) {
            return Optional.empty();
        }
        return Optional.of(
                CreateAppointmentResult.failure(
                        "Este plano atingiu o limite mensal de agendamentos ("
                                + max
                                + "/mês). "
                                + "Para continuar com novos agendamentos, faça upgrade do plano."));
    }

    public String createAppointment(
            TenantId tenantId,
            String isoDate,
            String localTime,
            String clientName,
            String serviceName,
            String conversationId,
            ZoneId calendarZone) {
        return createAppointmentWithResult(
                        tenantId, isoDate, localTime, clientName, serviceName, conversationId, calendarZone)
                .message();
    }

    /**
     * Nome do serviço do agendamento activo identificado por id (reagendamentos: usar o mesmo serviço do compromisso a
     * substituir).
     */
    public Optional<String> findServiceNameForActiveAppointment(
            TenantId tenantId, long appointmentId, String conversationId, ZoneId calendarZone) {
        if (calendarZone == null || conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return appointmentQuery
                .findByIdForTenantAndConversation(
                        tenantId, appointmentId, conversationId, calendarZone.getId())
                .map(TenantAppointmentListItem::serviceName)
                .filter(s -> s != null && !s.isBlank())
                .map(String::strip);
    }

    public String listTenantServicesForScheduling(TenantId tenantId) {
        List<String> available = tenantServiceCatalog.listActiveServiceNames(tenantId);
        if (available.isEmpty()) {
            return "No momento, não há serviços cadastrados para agendamento deste atendimento.";
        }
        return "Serviços disponíveis para agendamento:\n" + serviceChoicesWithOptionMapBlock(available);
    }

    /**
     * Indica se o nome corresponde a um serviço activo do tenant (match exacto alinhado a {@code tenant_services}).
     */
    public boolean isServiceInTenantCatalog(TenantId tenantId, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return false;
        }
        return tenantServiceCatalog.findServiceIdByName(tenantId, serviceName.strip()).isPresent();
    }

    /**
     * Tenta identificar um serviço do catálogo citado num texto livre do utilizador/modelo.
     * Preferência por nomes mais longos para evitar colisões parciais.
     */
    public Optional<String> resolveCatalogServiceMentionFromText(TenantId tenantId, String freeText) {
        if (freeText == null || freeText.isBlank()) {
            return Optional.empty();
        }
        String normalizedText = normalizeForServiceMatch(freeText);
        if (normalizedText.isBlank()) {
            return Optional.empty();
        }
        List<String> services = tenantServiceCatalog.listActiveServiceNames(tenantId);
        String best = null;
        int bestLen = -1;
        for (String service : services) {
            if (service == null || service.isBlank()) {
                continue;
            }
            String normalizedService = normalizeForServiceMatch(service);
            if (normalizedService.isBlank()) {
                continue;
            }
            if (containsWordSequence(normalizedText, normalizedService) && normalizedService.length() > bestLen) {
                best = service.strip();
                bestLen = normalizedService.length();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Mensagem quando o serviço pedido não existe no catálogo, listando as opções válidas.
     */
    public String buildUnknownServiceReplyWithOptions(TenantId tenantId, String requestedService) {
        return buildUnsupportedServiceMessage(tenantId, requestedService);
    }

    private String buildUnsupportedServiceMessage(TenantId tenantId, String requestedService) {
        String service = requestedService == null ? "" : requestedService.strip();
        if (!service.isEmpty()) {
            service = SchedulingExplicitTimeShortcut.sanitizeServiceHintAfterStrip(service);
        }
        List<String> available = tenantServiceCatalog.listActiveServiceNames(tenantId);
        if (available.isEmpty()) {
            return "O serviço mencionado não é atendido no momento. Ainda não há serviços cadastrados para este atendimento.";
        }
        String block = serviceChoicesWithOptionMapBlock(available);
        if (service.isEmpty()) {
            return "O serviço mencionado não é atendido. Estes são os serviços disponíveis:\n" + block;
        }
        if (SchedulingExplicitTimeShortcut.looksLikeTimeOrPreferencePhraseNotServiceName(service)) {
            return "O pedido de confirmação foi interpretado com um trecho que parece descrever horário, não o nome de um "
                    + "serviço. Se o serviço desejado não constar da lista abaixo, neste atendimento ainda não o "
                    + "oferecemos. Serviços disponíveis agora:\n"
                    + block;
        }
        return "O serviço \""
                + service
                + "\" não é atendido. Estes são os serviços disponíveis:\n"
                + block;
    }

    /**
     * Mesmo padrão em {@link #listTenantServicesForScheduling} e respostas de “serviço inválido”: lista numerada,
     * instrução e {@code [service_option_map:…]} para o normalizador / backend mapear a escolha do cliente.
     */
    private static String serviceChoicesWithOptionMapBlock(List<String> available) {
        return formatAvailableServicesForReply(available)
                + "\n\n"
                + "[service_option_map:"
                + buildServiceOptionMapAppendix(available)
                + "]";
    }

    private static String formatAvailableServicesForReply(List<String> available) {
        StringBuilder optionsBuilder = new StringBuilder();
        int max = Math.min(MAX_SERVICES_IN_REPLY, available.size());
        for (int i = 0; i < max; i++) {
            if (optionsBuilder.length() > 0) {
                optionsBuilder.append('\n');
            }
            optionsBuilder.append(i + 1).append(") ").append(available.get(i));
        }
        String options = optionsBuilder.toString();
        if (available.size() <= MAX_SERVICES_IN_REPLY) {
            return options + "\n\nResponda com o número do serviço desejado (ex.: 1).";
        }
        return options + "\n- ... e outros serviços cadastrados" + "\n\nResponda com o número do serviço desejado (ex.: 1).";
    }

    private static String buildServiceOptionMapAppendix(List<String> available) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(MAX_SERVICES_IN_REPLY, available.size());
        for (int i = 0; i < max; i++) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            String safeName = available.get(i).replace("|", "/").replace("]", ")");
            sb.append(i + 1).append("=").append(safeName);
        }
        return sb.toString();
    }

    private static LocalTime parseAppointmentLocalTime(String localTimeRaw) {
        if (localTimeRaw == null || localTimeRaw.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        String s = localTimeRaw.strip();
        try {
            return LocalTime.parse(s, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e2) {
                return LocalTime.MIDNIGHT;
            }
        }
    }

    private static String normalizeForServiceMatch(String value) {
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\s]", " ");
        s = s.replaceAll("\\s+", " ").strip();
        return s;
    }

    private static boolean containsWordSequence(String text, String sequence) {
        if (text.isBlank() || sequence.isBlank()) {
            return false;
        }
        return (" " + text + " ").contains(" " + sequence + " ");
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
        // Sucesso após gravação: a confirmação ao cliente segue no WhatsApp (async); o chat não repete a frase técnica.
        if (s.isEmpty()) {
            return true;
        }
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
        return getActiveAppointments(tenantId, conversationId, calendarZone, false);
    }

    /**
     * Lista agendamentos activos com rodapé contextual para cancelamento ou reagendamento.
     */
    public String getActiveAppointments(
            TenantId tenantId, String conversationId, ZoneId calendarZone, boolean forReschedule) {
        if (conversationId == null || conversationId.isBlank()) {
            return LIST_INVALID_SESSION_FRIENDLY_MESSAGE;
        }
        String conv = conversationId.strip();
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        List<TenantAppointmentListItem> rows =
                appointmentQuery.listAgendadoByConversationOrderedAscending(tenantId, conv, zoneId);
        if (rows.isEmpty()) {
            return NO_ACTIVE_APPOINTMENTS_FRIENDLY_MESSAGE;
        }
        Map<Integer, Long> optionToAppointmentId = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("*Agendamentos*").append("\n\n");
        if (rows.size() > 1) {
            sb.append(
                            forReschedule
                                    ? "Qual dos atendimentos abaixo você deseja reagendar?"
                                    : "Quais dos atendimentos abaixo gostaria de cancelar?")
                    .append("\n\n");
        } else {
            sb.append("Segue o seu agendamento ativo:").append("\n\n");
        }
        for (TenantAppointmentListItem r : rows) {
            String when = SLOT_FMT.format(r.startsAt().atZone(z));
            int shownId = Math.toIntExact(r.id());
            optionToAppointmentId.put(shownId, r.id());
            String svc = r.serviceName() != null ? r.serviceName().strip() : "";
            sb.append(shownId)
                    .append(") *")
                    .append(escapeAsterisksForWhatsAppBold(svc))
                    .append("* — ")
                    .append(when)
                    .append("\n");
        }
        sb.append("\n")
                .append(LIST_APPOINTMENTS_RESCHEDULE_HINT_FOOTER_PT)
                .append("\n");
        sb.append(CancelOptionMap.buildAppendix(optionToAppointmentId));
        return sb.toString();
    }

    /**
     * Existe exactamente um agendamento AGENDADO — adequado a cancelar automaticamente no início de um reagendamento.
     */
    public Optional<Long> getSingleActiveAppointmentId(
            TenantId tenantId, String conversationId, ZoneId calendarZone) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        List<TenantAppointmentListItem> rows =
                appointmentQuery.listAgendadoByConversationOrderedAscending(
                        tenantId, conversationId.strip(), zoneId);
        if (rows.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).id());
    }

    /**
     * Escolhe o agendamento AGENDADO a cancelar no início de um reagendamento: o único activo, ou o que coincide com
     * data/horário de origem na mensagem (ex.: reagendar de 11:00 para 15:00 no mesmo dia).
     */
    public Optional<Long> resolveActiveAppointmentIdForReschedule(
            TenantId tenantId, String conversationId, ZoneId calendarZone, String userMessage) {
        Optional<Long> only = getSingleActiveAppointmentId(tenantId, conversationId, calendarZone);
        if (only.isPresent()) {
            return only;
        }
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        Optional<ReagendamentoDeParaHint> hint =
                SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(userMessage.strip(), calendarZone);
        if (hint.isEmpty()) {
            return Optional.empty();
        }
        ReagendamentoDeParaHint h = hint.get();
        String zoneId = calendarZone != null ? calendarZone.getId() : ZoneId.systemDefault().getId();
        ZoneId z = calendarZone != null ? calendarZone : ZoneId.systemDefault();
        List<TenantAppointmentListItem> rows =
                appointmentQuery.listAgendadoByConversationOrderedAscending(
                        tenantId, conversationId.strip(), zoneId);
        for (TenantAppointmentListItem r : rows) {
            LocalDate d = r.startsAt().atZone(z).toLocalDate();
            LocalTime t = r.startsAt().atZone(z).toLocalTime();
            if (d.equals(h.day())
                    && t.getHour() == h.fromTime().getHour()
                    && t.getMinute() == h.fromTime().getMinute()) {
                return Optional.of(r.id());
            }
        }
        Optional<TenantAppointmentListItem> byDay =
                appointmentQuery.findActiveByConversationAndLocalDate(
                        tenantId, conversationId.strip(), h.day(), zoneId);
        if (byDay.isPresent()) {
            TenantAppointmentListItem r = byDay.get();
            LocalTime t = r.startsAt().atZone(z).toLocalTime();
            if (t.getHour() == h.fromTime().getHour() && t.getMinute() == h.fromTime().getMinute()) {
                return Optional.of(r.id());
            }
            long sameDayCount =
                    rows.stream().filter(x -> x.startsAt().atZone(z).toLocalDate().equals(h.day())).count();
            if (sameDayCount == 1) {
                LOG.warn(
                        "resolveActiveAppointmentIdForReschedule: fallback por único agendamento no dia "
                                + "(tenant={} conversationId={} day={} from={} escolhidoId={})",
                        tenantId.value(),
                        conversationId,
                        h.day(),
                        h.fromTime(),
                        r.id());
                return Optional.of(r.id());
            }
        }
        return Optional.empty();
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
            return CANCEL_INVALID_SESSION_FRIENDLY_MESSAGE;
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
            AppointmentAuditLog.appCancelled(row.id(), "not_applicable", "already");
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
            return CANCEL_TECHNICAL_ISSUE_FRIENDLY_MESSAGE;
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
            return CANCEL_CALENDAR_REMOVE_FAILED_FRIENDLY_MESSAGE;
        }
        if (!removedFromCalendar) {
            LOG.error(
                    "cancelAppointment: deleteCalendarEvent retornou false — calendário não sincronizado "
                            + "(appointmentId={} tenant={} googleEventId={})",
                    row.id(),
                    tenantId.value(),
                    gid);
            return CANCEL_CALENDAR_REMOVE_FAILED_FRIENDLY_MESSAGE;
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
            return CANCEL_PERSISTENCE_FAILED_FRIENDLY_MESSAGE;
        }
        try {
            String phone = CrmConversationSupport.phoneDigitsOnlyFromConversationId(conv).orElse("");
            eventPublisher.publishEvent(
                    new AppointmentCancelledEvent(
                            tenantId,
                            row.id(),
                            phone,
                            row.clientName() != null ? row.clientName().strip() : "",
                            row.serviceName() != null ? row.serviceName().strip() : "",
                            row.startsAt(),
                            z.getId()));
        } catch (RuntimeException e) {
            LOG.error(
                    "cancelAppointment: falha ao publicar AppointmentCancelledEvent (appointmentId={} tenant={})",
                    row.id(),
                    tenantId.value(),
                    e);
        }
        AppointmentAuditLog.appCancelled(row.id(), "deleted", "updated");
        // A confirmação ao utilizador final segue de forma assíncrona no WhatsApp
        // (AppointmentNotificationListener). Não enviar a frase técnica de sucesso no chat.
        return "";
    }

    private static String escapeAsterisksForWhatsAppBold(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("*", "·");
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
