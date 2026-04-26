package com.atendimento.cerebro.application.scheduling;

import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.conversation.SenderType;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Garante que o nome de serviço usado no agendamento veio de escolha do cliente (texto, lista numerada, ou
 * reagendamento explícito), e não de CRM ou "último agendamento" inferido.
 */
public final class SchedulingServiceAttribution {

    private static final Pattern NON_WORDS = Pattern.compile("[^a-z0-9áéíóúãõâêôç]+");
    private static final Set<String> STOPWORDS = new HashSet<>(
            Arrays.asList(
                    "o", "a", "os", "as", "de", "da", "do", "das", "dos", "um", "uma", "para", "pra", "por", "com",
                    "no", "na", "nos", "nas", "em", "ao", "à", "e", "ou"));

    private SchedulingServiceAttribution() {}

    /**
     * Conteúdo de mensagens do utilizador (e mensagem activa) para verificar se o serviço foi mencionado.
     */
    public static String mergeRecentUserText(List<Message> history, String latestUserMessage, int maxUserMessages) {
        if (latestUserMessage == null || latestUserMessage.isBlank()) {
            latestUserMessage = "";
        } else {
            latestUserMessage = latestUserMessage.strip();
        }
        if (history == null || history.isEmpty()) {
            return latestUserMessage;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < maxUserMessages; i--) {
            Message m = history.get(i);
            if (m.role() != MessageRole.USER) {
                continue;
            }
            if (m.senderType() == SenderType.BOT) {
                continue;
            }
            String c = m.content();
            if (c == null || c.isBlank()) {
                count++;
                continue;
            }
            if (sb.length() > 0) {
                sb.insert(0, '\n');
            }
            sb.insert(0, c.strip());
            count++;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(latestUserMessage);
        return sb.toString();
    }

    /**
     * O serviço foi explicitamente alinhado a esta conversa (selecção, texto do cliente) vs. inventado pelo modelo/CRM.
     */
    public static boolean isServiceAccountedByConversation(
            String serviceName, List<Message> history, String latestUserMessage) {
        if (serviceName == null || serviceName.isBlank() || history == null) {
            return false;
        }
        String svc = serviceName.strip();
        Optional<String> fromFlow = SchedulingExplicitTimeShortcut.parseServiceNameForCreateFromHistory(history);
        if (fromFlow.isPresent() && namesRoughlyEqual(fromFlow.get(), svc)) {
            return true;
        }
        String userBlob = mergeRecentUserText(history, latestUserMessage, 20);
        return userTextSupportsServiceName(svc, userBlob);
    }

    public static String trimServiceOrEmpty(String s) {
        if (s == null) {
            return "";
        }
        return s.strip();
    }

    private static boolean namesRoughlyEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizeForMatch(a).equals(normalizeForMatch(b));
    }

    public static boolean userTextSupportsServiceName(String serviceName, String userOnlyText) {
        if (serviceName == null || serviceName.isBlank() || userOnlyText == null || userOnlyText.isBlank()) {
            return false;
        }
        String nService = normalizeForMatch(serviceName);
        String nText = normalizeForMatch(userOnlyText);
        if (nText.contains(nService)) {
            return true;
        }
        String[] parts = nService.split("\\s+");
        for (String p : parts) {
            if (p.length() >= 4 && !STOPWORDS.contains(p) && nText.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForMatch(String s) {
        String t = s.toLowerCase(Locale.ROOT).strip();
        t = stripAccents(t);
        t = NON_WORDS.matcher(t).replaceAll(" ");
        t = t.replaceAll("\\s+", " ").strip();
        return t;
    }

    private static String stripAccents(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
}
