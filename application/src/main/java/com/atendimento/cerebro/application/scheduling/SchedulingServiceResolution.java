package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve o nome canónico do serviço para agendamento/confirmação com precedência estável, evitando herdar o primeiro
 * item do catálogo ou coincidências frágeis no transcript completo.
 */
public final class SchedulingServiceResolution {

    private static final Pattern[] DRAFT_ASSISTANT_PROSE_PATTERNS = {
        Pattern.compile("(?is)certo[,\\s]*\\*([^*\\n]+)\\*"),
        Pattern.compile("(?is)escolheu\\s+\\*([^*\\n]+)\\*"),
        Pattern.compile("(?is)servi[çc]o\\s+\\*([^*\\n]+)\\*"),
        Pattern.compile("(?is)para\\s+\\*([^*\\n]+)\\*"),
        Pattern.compile("(?is)agendar\\s+(?:o|a|um|uma)?\\s*\\*([^*\\n]+)\\*"),
    };
    private static final Pattern RECENT_SERVICE_PHRASE =
            Pattern.compile(
                    "(?is)servi[çc]o\\s+(?:de|do)?\\s*([\\p{L}0-9\\s\\-_/]+?)\\s+para\\b");

    private SchedulingServiceResolution() {}

    /**
     * Serviço a usar em {@code create_appointment} / confirmação directa no {@code ChatService}.
     *
     * @param interactiveServiceRowId {@code service_N} no turno de escolha de catálogo; em confirmações costuma ser
     *     {@code null} ou {@code confirm_*}.
     */
    public static Optional<String> resolveForCreateOrConfirm(
            TenantId tenantId,
            List<Message> history,
            String interactiveServiceRowId,
            AppointmentService appointmentService) {
        if (tenantId == null || appointmentService == null || history == null || history.isEmpty()) {
            return Optional.empty();
        }

        if (interactiveServiceRowId != null && !interactiveServiceRowId.isBlank()) {
            Optional<String> fromRow =
                    SchedulingUserReplyNormalizer.resolveSelectedServiceFromInteractiveRowId(
                            interactiveServiceRowId, history);
            if (fromRow.isPresent()) {
                return normalizeCatalogName(tenantId, fromRow.get(), appointmentService);
            }
        }

        Optional<String> token = SchedulingUserReplyNormalizer.parseLastSelectedServiceFromHistory(history);
        if (token.isPresent()) {
            return normalizeCatalogName(tenantId, token.get(), appointmentService);
        }

        Optional<String> draftMsg = resolveFromLastDraftAssistantMessage(tenantId, history, appointmentService);
        if (draftMsg.isPresent()) {
            return draftMsg;
        }

        Optional<String> afterCatalog =
                resolveFromUserTurnsAfterLastServiceCatalog(tenantId, history, appointmentService);
        if (afterCatalog.isPresent()) {
            return afterCatalog;
        }

        Optional<String> fromAvailability =
                SchedulingExplicitTimeShortcut.parseServiceNameForCreateFromHistory(history);
        if (fromAvailability.isPresent()) {
            return normalizeCatalogName(tenantId, fromAvailability.get(), appointmentService);
        }

        Optional<String> hint = SchedulingExplicitTimeShortcut.recoverServiceHintFromUserHistory(history);
        if (hint.isPresent()) {
            return appointmentService.resolveCatalogServiceMentionFromText(tenantId, hint.get());
        }

        Optional<String> fromRecentPhrase =
                resolveFromRecentServicePhrase(tenantId, history, appointmentService);
        if (fromRecentPhrase.isPresent()) {
            return fromRecentPhrase;
        }

        return Optional.empty();
    }

    /**
     * Rótulo de serviço para o cartão de horários (evita regex frágil sobre o transcript com o catálogo completo).
     */
    public static Optional<String> resolveForSlotCardTitle(
            TenantId tenantId,
            List<Message> history,
            String latestUserMessage,
            AppointmentService appointmentService) {
        Optional<String> primary = resolveForCreateOrConfirm(tenantId, history, null, appointmentService);
        if (primary.isPresent()) {
            return primary;
        }
        if (latestUserMessage != null && !latestUserMessage.isBlank()) {
            return appointmentService.resolveCatalogServiceMentionFromText(tenantId, latestUserMessage);
        }
        return Optional.empty();
    }

    private static Optional<String> normalizeCatalogName(
            TenantId tenantId, String raw, AppointmentService appointmentService) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.strip();
        if (appointmentService.isServiceInTenantCatalog(tenantId, s)) {
            return Optional.of(s);
        }
        return appointmentService.resolveCatalogServiceMentionFromText(tenantId, s);
    }

    private static Optional<String> resolveFromLastDraftAssistantMessage(
            TenantId tenantId, List<Message> history, AppointmentService appointmentService) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            String c = m.content();
            if (c == null || !c.contains(SchedulingUserReplyNormalizer.SCHEDULING_DRAFT_APPENDIX_TOKEN)) {
                continue;
            }
            if (SchedulingUserReplyNormalizer.isSupersededByLaterBookingOrCancellation(history, i)) {
                continue;
            }
            Optional<String> prose = tryDraftProsePatterns(tenantId, c, appointmentService);
            if (prose.isPresent()) {
                return prose;
            }
            String stripped = SchedulingUserReplyNormalizer.stripInternalSlotAppendix(c);
            return appointmentService.resolveCatalogServiceMentionFromText(tenantId, stripped);
        }
        return Optional.empty();
    }

    private static Optional<String> tryDraftProsePatterns(
            TenantId tenantId, String content, AppointmentService appointmentService) {
        String stripped = SchedulingUserReplyNormalizer.stripInternalSlotAppendix(content);
        for (Pattern p : DRAFT_ASSISTANT_PROSE_PATTERNS) {
            Matcher mm = p.matcher(stripped);
            if (mm.find()) {
                String chunk = mm.group(1).strip();
                Optional<String> r = appointmentService.resolveCatalogServiceMentionFromText(tenantId, chunk);
                if (r.isPresent()) {
                    return r;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveFromUserTurnsAfterLastServiceCatalog(
            TenantId tenantId, List<Message> history, AppointmentService appointmentService) {
        int idx = indexOfLastAssistantWithServiceCatalog(history);
        if (idx < 0) {
            return Optional.empty();
        }
        for (int i = idx + 1; i < history.size(); i++) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            String u = m.content();
            if (u == null || u.isBlank()) {
                continue;
            }
            String stripped = u.strip();
            List<Message> prefix = history.subList(0, i + 1);
            Optional<String> fromOption =
                    SchedulingUserReplyNormalizer.resolveSelectedServiceFromUserChoice(stripped, prefix);
            if (fromOption.isPresent()) {
                Optional<String> norm = normalizeCatalogName(tenantId, fromOption.get(), appointmentService);
                if (norm.isPresent()) {
                    return norm;
                }
            }
            if (SHORT_CONFIRM_LIKE.matcher(stripped.toLowerCase(Locale.ROOT)).matches()) {
                continue;
            }
            Optional<String> mention =
                    appointmentService.resolveCatalogServiceMentionFromText(tenantId, stripped);
            if (mention.isPresent()) {
                return mention;
            }
        }
        return Optional.empty();
    }

    private static final Pattern SHORT_CONFIRM_LIKE =
            Pattern.compile(
                    "^(sim|sí|confirmo|confirmado|pode|ok|isso|perfeito|fechado|pode\\s+ser|pode\\s+confirmar)\\b[.!\\s]*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static int indexOfLastAssistantWithServiceCatalog(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.ASSISTANT || m.senderType() != SenderType.BOT) {
                continue;
            }
            String c = m.content();
            if (c != null && c.contains(SchedulingUserReplyNormalizer.SERVICE_OPTION_MAP_APPENDIX_TOKEN)) {
                return i;
            }
        }
        return -1;
    }

    private static Optional<String> resolveFromRecentServicePhrase(
            TenantId tenantId, List<Message> history, AppointmentService appointmentService) {
        int lowerBound = Math.max(0, history.size() - 10);
        for (int i = history.size() - 1; i >= lowerBound; i--) {
            Message m = history.get(i);
            String c = m.content();
            if (c == null || c.isBlank()) {
                continue;
            }
            String sanitized = SchedulingUserReplyNormalizer.stripInternalSlotAppendix(c);
            Matcher matcher = RECENT_SERVICE_PHRASE.matcher(sanitized);
            while (matcher.find()) {
                String candidate = matcher.group(1).strip();
                Optional<String> mapped =
                        appointmentService.resolveCatalogServiceMentionFromText(tenantId, candidate);
                if (mapped.isPresent()) {
                    return mapped;
                }
            }
        }
        return Optional.empty();
    }
}
