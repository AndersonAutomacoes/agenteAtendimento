package com.atendimento.cerebro.infrastructure.reports;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Textos UTF-8 dos relatórios em {@code classpath:reports/reports_*.properties}. */
public final class ReportI18n {

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final Locale locale;
    private final Properties props = new Properties();

    public ReportI18n(Locale locale) {
        Objects.requireNonNull(locale);
        this.locale = locale;
        String suffix = suffixFor(locale);
        String path = "/reports/reports_" + suffix + ".properties";
        try (var in = ReportI18n.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load " + path, e);
        }
    }

    private static String suffixFor(Locale locale) {
        String tag = locale.toLanguageTag().replace('-', '_');
        return switch (tag) {
            case "pt_BR" -> "pt_BR";
            case "en", "en_US" -> "en";
            case "es", "es_ES" -> "es";
            case "zh_CN" -> "zh_CN";
            default -> {
                if ("pt".equals(locale.getLanguage())) {
                    yield "pt_BR";
                }
                if ("zh".equals(locale.getLanguage())) {
                    yield "zh_CN";
                }
                yield "en";
            }
        };
    }

    public static Locale parseLocaleParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return Locale.forLanguageTag("pt-BR");
        }
        return switch (raw.strip()) {
            case "pt-BR", "pt_BR" -> Locale.forLanguageTag("pt-BR");
            case "en" -> Locale.ENGLISH;
            case "es" -> Locale.forLanguageTag("es");
            case "zh-CN", "zh_CN" -> Locale.forLanguageTag("zh-CN");
            default -> Locale.forLanguageTag("pt-BR");
        };
    }

    public Locale locale() {
        return locale;
    }

    public String get(String key) {
        return props.getProperty(key, key);
    }

    public String formatInstantUtc(Instant instant) {
        return ISO_INSTANT.format(instant.atOffset(ZoneOffset.UTC));
    }

    /**
     * Data/hora legível no fuso UTC, conforme o idioma do relatório (ex.: pt-BR {@code 09/04/2026 18:05}).
     */
    public String formatInstantFriendly(Instant instant) {
        ZonedDateTime z = instant.atZone(ZoneOffset.UTC);
        return friendlyDateTimeFormatter().format(z);
    }

    private DateTimeFormatter friendlyDateTimeFormatter() {
        String suffix = suffixFor(locale);
        return switch (suffix) {
            case "zh_CN" -> DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withLocale(locale);
            case "en" -> DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(Locale.UK);
            default -> DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withLocale(locale);
        };
    }

    public String intentLabel(String rawIntent) {
        if (rawIntent == null || rawIntent.isBlank()) {
            return get("intent.OUTRO");
        }
        String k = "intent." + rawIntent.strip();
        return props.getProperty(k, rawIntent);
    }

    public String sentimentLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return get("sentiment._empty");
        }
        String k = "sentiment." + raw.strip();
        return props.getProperty(k, raw);
    }
}
