package com.atendimento.cerebro.infrastructure.calendar;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.atendimento.cerebro.infrastructure.config.CerebroGoogleCalendarProperties;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Google Calendar via <strong>google-api-client</strong> + <strong>google-api-services-calendar</strong>.
 * Credenciais com {@link GoogleCredentials#fromStream(InputStream)} (ficheiro ou {@code classpath:credentials.json}).
 *
 * <p>Escopo: {@link CalendarScopes#CALENDAR_EVENTS}. Fuso fixo {@link #CALENDAR_ZONE}. Duração padrão 30 minutos.
 */
@Component
@ConditionalOnProperty(prefix = "cerebro.google.calendar", name = "mock", havingValue = "false")
public class GoogleCalendarService {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarService.class);

    public static final ZoneId CALENDAR_ZONE = ZoneId.of("America/Bahia");

    public static final int DEFAULT_EVENT_DURATION_MINUTES = 30;

    private final CerebroGoogleCalendarProperties props;

    private static GoogleCalendarService springSelf(GoogleCalendarService service) {
        return service.self != null ? service.self : service;
    }

    private volatile Calendar calendarClient;

    private volatile String cachedServiceAccountEmail;

    /**
     * Auto-referência para o proxy Spring (Resilience4j) — evita auto-invocar {@code createEvent}/listas sem AOP.
     */
    @Lazy
    @Autowired
    @SuppressWarnings("checkstyle:VisibilityModifier")
    private GoogleCalendarService self;

    public GoogleCalendarService(CerebroGoogleCalendarProperties props) {
        this.props = props;
        // Não usamos o literal "primary" na API; o ID vem de tenant, YAML ou client_email do JSON.
        String cfgCal = props.getCalendarId();
        LOG.info(
                "GoogleCalendarService inicializado: mock=false; cerebro.google.calendar.calendar-id definido={} valor={}; "
                        + "credentialsClasspath={}; serviceAccountJsonPath={}",
                cfgCal != null && !cfgCal.isBlank(),
                cfgCal == null || cfgCal.isBlank() ? "<vazio>" : cfgCal,
                props.getCredentialsClasspath(),
                props.getServiceAccountJsonPath() == null || props.getServiceAccountJsonPath().isBlank()
                        ? "<vazio>"
                        : props.getServiceAccountJsonPath());
    }

    /**
     * Resolve o ID do calendário: (1) tenant; (2) {@code cerebro.google.calendar.calendar-id}; (3) {@code client_email}
     * do JSON.
     */
    public Optional<String> resolveEffectiveCalendarId(Optional<String> tenantGoogleCalendarId) {
        if (tenantGoogleCalendarId.isPresent() && !tenantGoogleCalendarId.get().isBlank()) {
            String id = tenantGoogleCalendarId.get().strip();
            LOG.info(
                    "resolveEffectiveCalendarId: origem={} calendarId={} (não é 'primary'; é o ID gravado no tenant)",
                    "tenant_configuration.google_calendar_id",
                    id);
            return Optional.of(id);
        }
        if (props.getCalendarId() != null && !props.getCalendarId().isBlank()) {
            String id = props.getCalendarId().strip();
            LOG.info(
                    "resolveEffectiveCalendarId: origem={} calendarId={}",
                    "cerebro.google.calendar.calendar-id",
                    id);
            return Optional.of(id);
        }
        try {
            ensureClientLoaded();
        } catch (IOException e) {
            LOG.warn("Não foi possível carregar credenciais para resolver calendário: {}", e.toString());
            return Optional.empty();
        }
        if (cachedServiceAccountEmail != null && !cachedServiceAccountEmail.isBlank()) {
            String id = cachedServiceAccountEmail.strip();
            LOG.warn(
                    "resolveEffectiveCalendarId: origem={} calendarId={} — "
                            + "este calendário é o da própria service account (equivalente ao \"principal\" dessa conta), "
                            + "não a agenda Oficina partilhada. Defina cerebro.google.calendar.calendar-id ou google_calendar_id no tenant.",
                    "credentials.client_email",
                    id);
            return Optional.of(id);
        }
        LOG.error(
                "resolveEffectiveCalendarId: nenhum ID resolvido (tenant vazio, calendar-id vazio, client_email ausente no JSON)");
        return Optional.empty();
    }

    /**
     * Cria evento com duração padrão de 30 minutos no fuso {@link #CALENDAR_ZONE}, usando o calendário global da
     * configuração (sem ID de tenant).
     */
    public GoogleCalendarCreatedEvent createEvent(String title, LocalDateTime startDateTime, String description)
            throws IOException {
        Optional<String> cal = resolveEffectiveCalendarId(Optional.empty());
        if (cal.isEmpty()) {
            throw new IllegalStateException(
                    "Calendário não configurado: defina cerebro.google.calendar.calendar-id ou credentials.json com client_email.");
        }
        return createEvent(title, startDateTime, description, cal.get());
    }

    /**
     * Cria evento (30 min) no calendário indicado — método principal para a integração Oficina / tenant.
     */
    public GoogleCalendarCreatedEvent createEvent(
            String title, LocalDateTime startDateTime, String description, String calendarId) throws IOException {
        if (calendarId == null || calendarId.isBlank()) {
            throw new IllegalArgumentException("calendarId is required");
        }
        return springSelf(this)
                .createEvent(
                        title, startDateTime, description, calendarId.strip(), DEFAULT_EVENT_DURATION_MINUTES);
    }

    /**
     * Cria evento com duração configurável (ex.: {@code cerebro.google.calendar.slot-minutes}) no fuso {@link #CALENDAR_ZONE}.
     */
    public GoogleCalendarCreatedEvent createEvent(
            String title,
            LocalDateTime startDateTime,
            String description,
            String calendarId,
            int durationMinutes)
            throws IOException {
        if (calendarId == null || calendarId.isBlank()) {
            throw new IllegalArgumentException("calendarId is required");
        }
        int mins = durationMinutes > 0 ? durationMinutes : DEFAULT_EVENT_DURATION_MINUTES;
        return springSelf(this)
                .createEventCore(
                        calendarId.strip(), title != null ? title : "", startDateTime, description, mins);
    }

    /**
     * Cria um evento de duração {@code durationMinutes}; {@code start} é interpretado em {@link #CALENDAR_ZONE}.
     */
    @Retry(name = "googleCalendarApi")
    public GoogleCalendarCreatedEvent createEventCore(
            String calendarId, String summary, LocalDateTime start, String description, int durationMinutes)
            throws IOException {
        if (calendarId == null || calendarId.isBlank()) {
            throw new IllegalArgumentException("calendarId is required");
        }
        ZonedDateTime startZoned = ZonedDateTime.of(start, CALENDAR_ZONE);
        Instant startInstant = startZoned.toInstant();
        ZonedDateTime endZoned = startZoned.plusMinutes(durationMinutes);
        Instant endInstant = endZoned.toInstant();

        String normalizedCalendarId = calendarId.strip();
        Event event =
                new Event()
                        .setSummary(summary != null ? summary : "")
                        .setDescription(description != null ? description : "");
        event.setStart(
                new EventDateTime()
                        .setDateTime(new DateTime(startInstant.toEpochMilli()))
                        .setTimeZone(CALENDAR_ZONE.getId()));
        event.setEnd(
                new EventDateTime()
                        .setDateTime(new DateTime(endInstant.toEpochMilli()))
                        .setTimeZone(CALENDAR_ZONE.getId()));

        LOG.info(
                "Google Calendar API: events.insert preparado calendarId={} summary={} startZoned={} endZoned={} durationMinutes={}",
                normalizedCalendarId,
                summary,
                startZoned,
                endZoned,
                durationMinutes);

        Calendar.Events.Insert insertRequest = calendar().events().insert(normalizedCalendarId, event);
        LOG.info(
                "Google Calendar API: a invocar Calendar.Events.Insert.execute() para calendarId={} (confirma que o HTTP foi enviado)",
                normalizedCalendarId);
        Event created = insertRequest.execute();
        LOG.info(
                "Google Calendar API: execute() concluído calendarId={} eventId={} htmlLink={} status={} iCalUID={}",
                normalizedCalendarId,
                created.getId(),
                created.getHtmlLink(),
                created.getStatus(),
                created.getICalUID());

        String id = created.getId() != null ? created.getId() : "unknown";
        String link = created.getHtmlLink() != null ? created.getHtmlLink() : "";
        LOG.info(
                "Google Calendar: evento persistido na API — calendarId={} eventId={} htmlLink={} start={} end={} zone={}",
                normalizedCalendarId,
                id,
                link,
                startInstant,
                endInstant,
                CALENDAR_ZONE.getId());
        return new GoogleCalendarCreatedEvent(id, link, startInstant, endInstant);
    }

    @Retry(name = "googleCalendarApi")
    public Events listEvents(String calendarId, DateTime timeMin, DateTime timeMax) throws Exception {
        LOG.info(
                "Google Calendar API: events.list a invocar execute() calendarId={} timeMin={} timeMax={}",
                calendarId,
                timeMin,
                timeMax);
        Events out =
                calendar()
                        .events()
                        .list(calendarId)
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .execute();
        int n = out.getItems() == null ? 0 : out.getItems().size();
        LOG.info(
                "Google Calendar API: events.list execute() concluído calendarId={} itemsCount={}",
                calendarId,
                n);
        return out;
    }

    /**
     * Apaga o evento no calendário. Após {@code delete}, confirma com {@code get} que o evento deixou de estar activo
     * (404/410 ou estado {@code cancelled}) antes de considerar concluído — evita confirmar ao cliente sem remoção real.
     *
     * <p>404 no {@code delete} trata-se como idempotência (já inexistente neste calendário); ainda assim verifica-se com
     * {@code get}.
     */
    @Retry(name = "googleCalendarApi")
    public void deleteEvent(String calendarId, String eventId) throws IOException {
        if (calendarId == null || calendarId.isBlank() || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("calendarId and eventId are required");
        }
        String cal = calendarId.strip();
        String eid = eventId.strip();
        try {
            calendar().events().delete(cal, eid).execute();
            LOG.info("Google Calendar: delete execute() OK calendarId={} eventId={}", cal, eid);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                LOG.debug("Google Calendar: delete devolveu 404 (já inexistente) calendarId={} eventId={}", cal, eid);
            } else {
                throw e;
            }
        }
        verifyEventNoLongerActiveOrAbsent(cal, eid);
    }

    /**
     * Garante que o evento não aparece como confirmado após delete. 404/410 = ausente; {@code status=cancelled} = aceite.
     */
    private void verifyEventNoLongerActiveOrAbsent(String calendarId, String eventId) throws IOException {
        try {
            Event ev = calendar().events().get(calendarId, eventId).execute();
            if (ev != null) {
                String st = ev.getStatus();
                if (st != null && "cancelled".equalsIgnoreCase(st)) {
                    LOG.debug("Google Calendar: verificação pós-delete — evento em estado cancelled id={}", eventId);
                    return;
                }
                LOG.error(
                        "Google Calendar: verificação pós-delete falhou — evento ainda activo calendarId={} eventId={} status={}",
                        calendarId,
                        eventId,
                        st);
                throw new IOException(
                        "Google Calendar: o evento continua activo após delete (eventId=" + eventId + ", status=" + st + ")");
            }
        } catch (GoogleJsonResponseException e) {
            int code = e.getStatusCode();
            if (code == 404 || code == 410) {
                LOG.info(
                        "Google Calendar: verificação pós-delete OK (evento ausente) calendarId={} eventId={} http={}",
                        calendarId,
                        eventId,
                        code);
                return;
            }
            throw e;
        }
    }

    /**
     * Eventos não cancelados cuja interseção com {@code [startInclusive, endExclusive)} não é vazia (evita duplicidade
     * antes de {@link #createEvent}).
     */
    public List<Event> findEventsOverlapping(Instant startInclusive, Instant endExclusive, String calendarId)
            throws Exception {
        if (calendarId == null || calendarId.isBlank()) {
            throw new IllegalArgumentException("calendarId is required");
        }
        if (startInclusive == null || endExclusive == null || !endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("invalid interval");
        }
        String cal = calendarId.strip();
        DateTime tMin = new DateTime(startInclusive.toEpochMilli());
        DateTime tMax = new DateTime(endExclusive.toEpochMilli());
        Events batch = springSelf(this).listEvents(cal, tMin, tMax);
        List<Event> out = new ArrayList<>();
        if (batch.getItems() == null) {
            return List.of();
        }
        for (Event ev : batch.getItems()) {
            if (ev == null) {
                continue;
            }
            if (ev.getStatus() != null && "cancelled".equalsIgnoreCase(ev.getStatus())) {
                continue;
            }
            Instant es = eventStartInstant(ev);
            Instant ee = eventEndInstant(ev);
            if (es == null || ee == null || !ee.isAfter(es)) {
                continue;
            }
            if (intervalsOverlap(startInclusive, endExclusive, es, ee)) {
                out.add(ev);
            }
        }
        return List.copyOf(out);
    }

    private static boolean intervalsOverlap(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private static Instant eventStartInstant(Event ev) {
        var edt = ev.getStart();
        if (edt == null) {
            return null;
        }
        if (edt.getDateTime() != null) {
            return Instant.ofEpochMilli(edt.getDateTime().getValue());
        }
        if (edt.getDate() != null) {
            return Instant.ofEpochMilli(edt.getDate().getValue());
        }
        return null;
    }

    private static Instant eventEndInstant(Event ev) {
        var edt = ev.getEnd();
        if (edt == null) {
            return null;
        }
        if (edt.getDateTime() != null) {
            return Instant.ofEpochMilli(edt.getDateTime().getValue());
        }
        if (edt.getDate() != null) {
            return Instant.ofEpochMilli(edt.getDate().getValue());
        }
        return null;
    }

    private void ensureClientLoaded() throws IOException {
        calendar();
    }

    private Calendar calendar() throws IOException {
        if (calendarClient != null) {
            return calendarClient;
        }
        synchronized (this) {
            if (calendarClient != null) {
                return calendarClient;
            }
            byte[] jsonBytes = loadCredentialsBytes();
            cachedServiceAccountEmail = parseClientEmail(jsonBytes);
            try (InputStream in = new ByteArrayInputStream(jsonBytes)) {
                GoogleCredentials credentials =
                        GoogleCredentials.fromStream(in).createScoped(List.of(CalendarScopes.CALENDAR_EVENTS));
                calendarClient =
                        new Calendar.Builder(
                                        GoogleNetHttpTransport.newTrustedTransport(),
                                        GsonFactory.getDefaultInstance(),
                                        new HttpCredentialsAdapter(credentials))
                                .setApplicationName("atendimento-cerebro")
                                .build();
            } catch (Exception e) {
                if (e instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException(e);
            }
            return calendarClient;
        }
    }

    private byte[] loadCredentialsBytes() throws IOException {
        String path = props.getServiceAccountJsonPath();
        if (path != null && !path.isBlank()) {
            Path p = Path.of(path.strip());
            if (Files.isRegularFile(p)) {
                LOG.debug("Google Calendar: credenciais a partir do ficheiro {}", p.toAbsolutePath());
                return Files.readAllBytes(p);
            }
            LOG.warn(
                    "cerebro.google.calendar.service-account-json-path não é ficheiro válido ({}). A usar classpath:{}",
                    p.toAbsolutePath(),
                    props.getCredentialsClasspath());
        }
        String cp = props.getCredentialsClasspath() != null ? props.getCredentialsClasspath() : "credentials.json";
        ClassPathResource res = new ClassPathResource(cp);
        if (!res.exists()) {
            throw new IllegalStateException(
                    "Credenciais Google Calendar não encontradas: nem ficheiro em service-account-json-path nem classpath:"
                            + cp);
        }
        LOG.debug("Google Calendar: credenciais a partir do classpath {}", cp);
        try (InputStream in = res.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static String parseClientEmail(byte[] jsonBytes) {
        if (jsonBytes == null || jsonBytes.length == 0) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("client_email") && !o.get("client_email").isJsonNull()) {
                return o.get("client_email").getAsString();
            }
        } catch (Exception e) {
            LOG.warn("Não foi possível ler client_email do JSON de credenciais: {}", e.toString());
        }
        return null;
    }

    public static Instant startOfDayInstant(LocalDate day) {
        return day.atStartOfDay(CALENDAR_ZONE).toInstant();
    }

    public static Instant endOfDayExclusiveInstant(LocalDate day) {
        return day.plusDays(1).atStartOfDay(CALENDAR_ZONE).toInstant();
    }
}
