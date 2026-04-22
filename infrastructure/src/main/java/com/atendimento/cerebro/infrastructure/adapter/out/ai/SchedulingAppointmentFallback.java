package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitários para extrair datas/horas de texto (testes e diagnóstico). A persistência automática de agendamentos
 * a partir do transcript está desactivada; o agendamento só ocorre via {@code create_appointment}.
 */
public final class SchedulingAppointmentFallback {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulingAppointmentFallback.class);

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    /** dd/MM, dd/MM/yy ou dd/MM/yyyy */
    private static final Pattern BR_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
    /** Ex.: «18 de abril de 2026», «3 de maio» (ano omisso = ano civil actual na zona). */
    private static final Pattern PT_SPOKEN_DATE =
            Pattern.compile(
                    "(?i)\\b(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|mar[cç]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s*(?:de\\s+(\\d{4}))?\\b");
    private static final Pattern TIME_HM = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern SERVICE_HINT =
            Pattern.compile(
                    "(?i)(troca\\s*de\\s*óleo|troca\\s*de\\s*oleo|revisão\\s*preventiva|revisão|revisao|balanceamento|alinhamento|óleo|oleo)");

    private SchedulingAppointmentFallback() {}

    /**
     * Desactivado: o agendamento só pode ocorrer via {@code create_appointment} após confirmação explícita do
     * utilizador; não se infere data/hora do texto do chat.
     *
     * @return sempre vazio
     */
    public static Optional<String> tryPersist(
            AICompletionRequest request, AppointmentSchedulingPort port, ZoneId zone) {
        LOG.debug(
                "Agendamento: persistência fallback desactivada (tenant={}).",
                request.tenantId() != null ? request.tenantId().value() : "?");
        return Optional.empty();
    }

    private static String buildTranscript(AICompletionRequest request) {
        List<String> lines = new ArrayList<>();
        List<Message> hist = request.conversationHistory();
        int from = Math.max(0, hist.size() - 12);
        for (int i = from; i < hist.size(); i++) {
            lines.add(hist.get(i).content());
        }
        lines.add(request.userMessage());
        return String.join("\n", lines);
    }

    static Optional<LocalDate> lastDateInTranscript(String blob, ZoneId zone) {
        int currentYear = LocalDate.now(zone).getYear();
        List<DateHit> hits = new ArrayList<>();

        Matcher iso = ISO_DATE.matcher(blob);
        while (iso.find()) {
            try {
                hits.add(new DateHit(iso.end(), LocalDate.parse(iso.group(1))));
            } catch (DateTimeParseException ignored) {
                // skip
            }
        }

        Matcher br = BR_DATE.matcher(blob);
        while (br.find()) {
            int d = Integer.parseInt(br.group(1));
            int m = Integer.parseInt(br.group(2));
            String yStr = br.group(3);
            int year;
            if (yStr == null || yStr.isEmpty()) {
                year = currentYear;
            } else if (yStr.length() == 2) {
                year = 2000 + Integer.parseInt(yStr);
            } else {
                year = Integer.parseInt(yStr);
            }
            try {
                hits.add(new DateHit(br.end(), LocalDate.of(year, m, d)));
            } catch (Exception ignored) {
                // skip invalid
            }
        }

        Matcher spoken = PT_SPOKEN_DATE.matcher(blob);
        while (spoken.find()) {
            int day = Integer.parseInt(spoken.group(1));
            Optional<Integer> monthNum = monthNumberPortuguese(spoken.group(2));
            if (monthNum.isEmpty()) {
                continue;
            }
            String yStr = spoken.group(3);
            int year = yStr != null && !yStr.isEmpty() ? Integer.parseInt(yStr) : currentYear;
            try {
                hits.add(new DateHit(spoken.end(), LocalDate.of(year, monthNum.get(), day)));
            } catch (Exception ignored) {
                // skip invalid
            }
        }
        return hits.stream().max(Comparator.comparingInt(DateHit::end)).map(DateHit::date);
    }

    private static Optional<Integer> monthNumberPortuguese(String rawMonth) {
        String k =
                Normalizer.normalize(rawMonth.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
                        .replace('ç', 'c');
        return switch (k) {
            case "janeiro" -> Optional.of(1);
            case "fevereiro" -> Optional.of(2);
            case "marco" -> Optional.of(3);
            case "abril" -> Optional.of(4);
            case "maio" -> Optional.of(5);
            case "junho" -> Optional.of(6);
            case "julho" -> Optional.of(7);
            case "agosto" -> Optional.of(8);
            case "setembro" -> Optional.of(9);
            case "outubro" -> Optional.of(10);
            case "novembro" -> Optional.of(11);
            case "dezembro" -> Optional.of(12);
            default -> Optional.empty();
        };
    }

    private record DateHit(int end, LocalDate date) {}

    static Optional<LocalTime> lastTimeInTranscript(String blob) {
        LocalTime last = null;
        Matcher tm = TIME_HM.matcher(blob);
        while (tm.find()) {
            int h = Integer.parseInt(tm.group(1));
            int min = Integer.parseInt(tm.group(2));
            if (h >= 0 && h <= 23 && min >= 0 && min <= 59) {
                try {
                    last = LocalTime.of(h, min);
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        return Optional.ofNullable(last);
    }

    private static String formatHm(LocalTime t) {
        return String.format(Locale.ROOT, "%d:%02d", t.getHour(), t.getMinute());
    }

    static String inferClient(String blob) {
        Matcher m = Pattern.compile("(?i)Sr\\.?\\s*([A-Za-zÀ-ú]{2,24})").matcher(blob);
        String last = null;
        while (m.find()) {
            last = m.group(1).strip();
        }
        return last != null ? last : "Cliente";
    }

    static String inferService(String blob) {
        Matcher m = SERVICE_HINT.matcher(blob);
        String last = null;
        while (m.find()) {
            last = capitalizeWords(m.group(1).replaceAll("\\s+", " ").strip());
        }
        return last != null ? last : "Serviço agendado";
    }

    private static String capitalizeWords(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String[] p = s.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : p) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().strip();
    }
}
