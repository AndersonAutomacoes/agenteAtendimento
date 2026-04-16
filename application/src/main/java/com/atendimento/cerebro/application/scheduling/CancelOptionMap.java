package com.atendimento.cerebro.application.scheduling;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mapeamento do número mostrado na lista de cancelamento → {@code appointmentId} na base, persistido no apêndice
 * {@code [cancel_option_map:2=2,5=7]} na mensagem do assistente (removido na saída WhatsApp). A chave é o prefixo exibido
 * em cada linha (hoje o ID/PK do agendamento); o valor é o mesmo ID usado em {@code cancel_appointment}.
 */
public final class CancelOptionMap {

    public static final String APPENDIX_PREFIX = "[cancel_option_map:";

    private static final Pattern MAP_BLOCK =
            Pattern.compile("\\[cancel_option_map:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPCAO_NUMERO =
            Pattern.compile("(?:op(ç|c)ão|opcao)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private CancelOptionMap() {}

    public static String buildAppendix(Map<Integer, Long> optionOneBasedToAppointmentId) {
        if (optionOneBasedToAppointmentId == null || optionOneBasedToAppointmentId.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(APPENDIX_PREFIX);
        boolean first = true;
        for (var e : new TreeMap<>(optionOneBasedToAppointmentId).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    /** Último bloco {@code [cancel_option_map:…]} encontrado no texto (histórico + mensagem). */
    public static Map<Integer, Long> parseLastFromText(String blob) {
        if (blob == null || blob.isBlank()) {
            return Map.of();
        }
        String last = null;
        Matcher m = MAP_BLOCK.matcher(blob);
        while (m.find()) {
            last = m.group(1);
        }
        if (last == null || last.isBlank()) {
            return Map.of();
        }
        Map<Integer, Long> out = new HashMap<>();
        for (String part : last.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int k = Integer.parseInt(p.substring(0, eq).strip());
                long v = Long.parseLong(p.substring(eq + 1).strip());
                out.put(k, v);
            } catch (NumberFormatException ignored) {
                // skip malformed fragment
            }
        }
        return out;
    }

    /**
     * Converte «opção 1», «opcao 2» ou só o índice «1» para o {@code appointmentId} real quando existe mapa na
     * conversa; caso contrário devolve o texto original (ID directo).
     */
    public static String resolveAppointmentIdForCancel(String appointmentIdRaw, String transcriptBlob) {
        if (appointmentIdRaw == null || appointmentIdRaw.isBlank()) {
            return appointmentIdRaw;
        }
        String raw = appointmentIdRaw.strip();
        Map<Integer, Long> map = parseLastFromText(transcriptBlob != null ? transcriptBlob : "");
        if (map.isEmpty()) {
            map = SchedulingCancelSessionCapture.getSelectionMap();
        }
        if (map.isEmpty()) {
            return raw;
        }
        String norm = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Matcher op = OPCAO_NUMERO.matcher(norm);
        if (op.find()) {
            int idx = Integer.parseInt(op.group(2));
            if (map.containsKey(idx)) {
                return Long.toString(map.get(idx));
            }
        }
        if (raw.matches("^\\d+$")) {
            int n = Integer.parseInt(raw);
            if (map.containsKey(n)) {
                return Long.toString(map.get(n));
            }
        }
        return raw;
    }
}
