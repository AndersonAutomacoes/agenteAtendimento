package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.dto.AudioTranscriptionResult;
import com.atendimento.cerebro.application.port.out.AudioTranscriptionPort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import com.atendimento.cerebro.infrastructure.config.GroqSttProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cerebro.stt.groq", name = "api-key")
public class GroqAudioTranscriptionAdapter implements AudioTranscriptionPort {

    private static final Logger LOG = LoggerFactory.getLogger(GroqAudioTranscriptionAdapter.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_DOWNLOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final long MAX_AUDIO_BYTES = 26L * 1024L * 1024L;
    private static final String GROQ_TRANSCRIPTION_PROMPT =
            "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, horário, agendamento, segunda, terça, quarta, quinta, sexta, sábado, manhã, tarde, noite.";

    private final GroqSttProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final String evolutionBaseUrlOverride;

    public GroqAudioTranscriptionAdapter(
            GroqSttProperties props,
            ObjectMapper objectMapper,
            TenantConfigurationStorePort tenantConfigurationStore,
            @org.springframework.beans.factory.annotation.Value("${cerebro.whatsapp.evolution.base-url-override:}")
                    String evolutionBaseUrlOverride) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.evolutionBaseUrlOverride = evolutionBaseUrlOverride != null ? evolutionBaseUrlOverride : "";
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    @Override
    public Optional<AudioTranscriptionResult> transcribe(
            TenantId tenantId, String mediaUrl, String mimeType, String providerMessageId) {
        Instant startedAt = Instant.now();
        String tenant = tenantId != null ? tenantId.value() : "unknown";
        String safeMime = mimeType != null ? mimeType : "";
        LOG.info("[AXEZAP-STT] Áudio recebido | tenant={} mime={} source={}", tenant, safeMime, sanitizeUrl(mediaUrl));

        try {
            if (mediaUrl == null || mediaUrl.isBlank()) {
                LOG.warn("[AXEZAP-STT] mediaUrl ausente para tenant={}", tenant);
                return Optional.empty();
            }
            if (props.getApiKey() == null || props.getApiKey().isBlank()) {
                LOG.warn("[AXEZAP-STT] GROQ_API_KEY ausente; transcrição ignorada tenant={}", tenant);
                return Optional.empty();
            }
            DownloadedAudio downloaded =
                    maybeDownloadDecodedFromEvolution(tenantId, providerMessageId, mediaUrl, mimeType, tenant)
                            .orElseGet(
                                    () -> {
                                        try {
                                            return downloadAudio(mediaUrl, mimeType, tenant);
                                        } catch (IOException | InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    });

            // Produção: sempre tenta áudio convertido para WAV antes de fallback para payload original.
            Optional<AudioTranscriptionResult> converted = convertWithFfmpegAndRetry(downloaded.bytes(), downloaded.filename());
            converted.ifPresent(this::logTranscriptionResult);
            if (converted.isPresent()) {
                return converted;
            }

            Optional<AudioTranscriptionResult> directResult = transcribeWithGroq(downloaded.bytes(), downloaded.filename());
            directResult.ifPresent(this::logTranscriptionResult);
            return directResult;
        } catch (HttpTimeoutException e) {
            LOG.warn("[AXEZAP-STT] timeout em download/transcrição tenant={} cause={}", tenant, e.toString());
            return Optional.empty();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException || e.getCause() instanceof InterruptedException) {
                LOG.warn("[AXEZAP-STT] falha no download de mídia tenant={} cause={}", tenant, e.getCause().toString());
                if (e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return Optional.empty();
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("[AXEZAP-STT] interrupção durante transcrição tenant={}", tenant, e);
            return Optional.empty();
        } catch (Exception e) {
            LOG.warn("[AXEZAP-STT] falha de transcrição tenant={} cause={}", tenant, e.toString());
            return Optional.empty();
        } finally {
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();
            LOG.info("[AXEZAP-STT] Tempo de transcrição: {}ms | tenant={}", elapsedMs, tenant);
            if (elapsedMs > 2000) {
                LOG.warn(
                        "[AXEZAP-STT] Latência alta detectada ({}ms > 2000ms) tenant={} - considere reduzir timeout/configurar otimização",
                        elapsedMs,
                        tenant);
            }
        }
    }

    private DownloadedAudio downloadAudio(String mediaUrl, String providedMimeType, String tenant)
            throws IOException, InterruptedException {
        LOG.info("[AXEZAP-STT] Download de mídia iniciado | tenant={} url={}", tenant, sanitizeUrl(mediaUrl));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mediaUrl))
                .timeout(resolveDownloadTimeout())
                .header("Accept", "audio/*,application/octet-stream")
                .header("User-Agent", "AxeZap-STT/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("falha download audio status=" + status);
        }
        byte[] bytes = response.body() != null ? response.body() : new byte[0];
        if (bytes.length == 0) {
            throw new IOException("audio vazio");
        }
        if (bytes.length > MAX_AUDIO_BYTES) {
            throw new IOException("audio excede limite de 26MB");
        }
        String responseContentType = response.headers().firstValue("Content-Type").orElse("");
        String contentType = !responseContentType.isBlank() ? responseContentType : (providedMimeType != null ? providedMimeType : "");
        String filename = inferFilename(mediaUrl, contentType);
        LOG.info(
                "[AXEZAP-STT] Download de mídia concluído | tenant={} bytes={} contentType={} filename={}",
                tenant,
                bytes.length,
                contentType,
                filename);
        return new DownloadedAudio(bytes, contentType, filename);
    }

    private Optional<AudioTranscriptionResult> transcribeWithGroq(byte[] bytes, String filename)
            throws IOException, InterruptedException {
        String boundary = "----axezap-" + UUID.randomUUID();
        HttpRequest.BodyPublisher body = buildMultipartBody(bytes, filename, boundary);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getApiUrl()))
                .timeout(resolveRequestTimeout())
                .header("Authorization", "Bearer " + props.getApiKey().strip())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body)
                .build();

        LOG.info(
                "[AXEZAP-STT] Envio para Groq iniciado | model={} language={} filename={}",
                props.getModel(),
                props.getLanguage(),
                filename);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        String bodyText = response.body() != null ? response.body() : "";
        LOG.info("[AXEZAP-STT] Resposta Groq recebida | status={}", status);
        if (status == 429) {
            LOG.warn("[AXEZAP-STT] Groq rate limit (429)");
            return Optional.empty();
        }
        if (status >= 500) {
            LOG.warn("[AXEZAP-STT] Groq indisponível status={}", status);
            return Optional.empty();
        }
        if (status < 200 || status >= 300) {
            LOG.warn("[AXEZAP-STT] Groq rejeitou áudio status={} body={}", status, truncate(bodyText, 300));
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(bodyText);
        String text = root.path("text").asText("").strip();
        if (text.isBlank()) {
            LOG.warn("[AXEZAP-STT] Groq respondeu sem texto útil");
            return Optional.empty();
        }
        String language = props.getLanguage() == null || props.getLanguage().isBlank() ? "pt" : props.getLanguage().strip();
        return Optional.of(new AudioTranscriptionResult(text, null, language));
    }

    private Optional<AudioTranscriptionResult> convertWithFfmpegAndRetry(byte[] originalBytes, String originalFilename)
            throws IOException, InterruptedException {
        Path sourceFile = null;
        Path targetFile = null;
        try {
            LOG.info("[AXEZAP-STT] Conversão FFmpeg iniciada | input={}", originalFilename);
            sourceFile = Files.createTempFile("axezap-stt-in-", extensionFromFilename(originalFilename));
            targetFile = Files.createTempFile("axezap-stt-out-", ".wav");
            Files.write(sourceFile, originalBytes);

            Process process = new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-i",
                            sourceFile.toAbsolutePath().toString(),
                            "-ar",
                            "16000",
                            "-ac",
                            "1",
                            targetFile.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.warn("[AXEZAP-STT] ffmpeg timeout durante conversão ogg->wav");
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                LOG.warn("[AXEZAP-STT] ffmpeg falhou ao converter áudio (exit={})", process.exitValue());
                return Optional.empty();
            }
            byte[] wavBytes = Files.readAllBytes(targetFile);
            if (wavBytes.length == 0) {
                return Optional.empty();
            }
            LOG.info("[AXEZAP-STT] Conversão FFmpeg concluída | outputBytes={}", wavBytes.length);
            return transcribeWithGroq(wavBytes, "audio.wav");
        } catch (IOException e) {
            LOG.warn("[AXEZAP-STT] ffmpeg indisponível ou falha de conversão: {}", e.toString());
            return Optional.empty();
        } finally {
            deleteQuietly(sourceFile);
            deleteQuietly(targetFile);
        }
    }

    private HttpRequest.BodyPublisher buildMultipartBody(byte[] fileBytes, String filename, String boundary) {
        String language = props.getLanguage() == null || props.getLanguage().isBlank() ? "pt" : props.getLanguage().strip();
        String model = props.getModel() == null || props.getModel().isBlank() ? "whisper-large-v3" : props.getModel().strip();
        String contentType = mimeByFilename(filename);
        List<byte[]> chunks = new ArrayList<>();

        chunks.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add(("Content-Disposition: form-data; name=\"model\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add((model + "\r\n").getBytes(StandardCharsets.UTF_8));

        chunks.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add(("Content-Disposition: form-data; name=\"language\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add((language + "\r\n").getBytes(StandardCharsets.UTF_8));

        chunks.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add(("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add((GROQ_TRANSCRIPTION_PROMPT + "\r\n").getBytes(StandardCharsets.UTF_8));

        chunks.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add(
                ("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        chunks.add(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        chunks.add(fileBytes);
        chunks.add("\r\n".getBytes(StandardCharsets.UTF_8));
        chunks.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.BodyPublishers.ofByteArrays(chunks);
    }

    private static String sanitizeUrl(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return "empty";
        }
        try {
            URI uri = URI.create(mediaUrl);
            String host = uri.getHost() != null ? uri.getHost() : "unknown";
            return host + (uri.getPath() != null ? uri.getPath() : "");
        } catch (Exception ignored) {
            return "invalid-url";
        }
    }

    private static String inferFilename(String mediaUrl, String contentType) {
        String fromUrl = "audio" + extensionFromContentType(contentType);
        try {
            URI uri = URI.create(mediaUrl);
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                int slash = path.lastIndexOf('/');
                String candidate = slash >= 0 ? path.substring(slash + 1) : path;
                if (!candidate.isBlank() && candidate.contains(".")) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            return fromUrl;
        }
        return fromUrl;
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        String low = contentType.toLowerCase(Locale.ROOT);
        if (low.contains("ogg")) {
            return ".ogg";
        }
        if (low.contains("webm")) {
            return ".webm";
        }
        if (low.contains("wav")) {
            return ".wav";
        }
        if (low.contains("mpeg") || low.contains("mp3")) {
            return ".mp3";
        }
        if (low.contains("m4a") || low.contains("mp4")) {
            return ".m4a";
        }
        return ".bin";
    }

    private static String extensionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return ".bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".bin";
        }
        return filename.substring(dot);
    }

    private static String mimeByFilename(String filename) {
        String low = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (low.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (low.endsWith(".webm")) {
            return "audio/webm";
        }
        if (low.endsWith(".wav")) {
            return "audio/wav";
        }
        if (low.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (low.endsWith(".m4a") || low.endsWith(".mp4")) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private Duration resolveRequestTimeout() {
        long configured = props.getRequestTimeoutMs();
        if (configured <= 0) {
            return DEFAULT_REQUEST_TIMEOUT;
        }
        return Duration.ofMillis(configured);
    }

    private Duration resolveDownloadTimeout() {
        long configured = props.getRequestTimeoutMs();
        if (configured <= 0) {
            return DEFAULT_DOWNLOAD_TIMEOUT;
        }
        long millis = Math.max(configured, DEFAULT_DOWNLOAD_TIMEOUT.toMillis());
        return Duration.ofMillis(millis);
    }

    private Optional<DownloadedAudio> maybeDownloadDecodedFromEvolution(
            TenantId tenantId,
            String providerMessageId,
            String mediaUrl,
            String mimeType,
            String tenantForLog) {
        if (!isLikelyEncryptedEvolutionMedia(mediaUrl, mimeType) || providerMessageId == null || providerMessageId.isBlank()) {
            return Optional.empty();
        }
        if (tenantId == null) {
            return Optional.empty();
        }
        var cfgOpt = tenantConfigurationStore.findByTenantId(tenantId);
        if (cfgOpt.isEmpty()) {
            return Optional.empty();
        }
        var cfg = cfgOpt.get();
        if (cfg.whatsappProviderType() != WhatsAppProviderType.EVOLUTION
                || cfg.whatsappApiKey() == null
                || cfg.whatsappApiKey().isBlank()
                || cfg.whatsappInstanceId() == null
                || cfg.whatsappInstanceId().isBlank()) {
            return Optional.empty();
        }
        String baseRaw = evolutionBaseUrlOverride.isBlank() ? cfg.whatsappBaseUrl() : evolutionBaseUrlOverride;
        String base = trimTrailingSlash(baseRaw);
        if (base.isBlank()) {
            return Optional.empty();
        }

        LOG.info(
                "[AXEZAP-STT] mídia criptografada detectada (.enc). Tentando media decode via Evolution | tenant={} messageId={}",
                tenantForLog,
                providerMessageId);
        String endpoint = base + "/chat/getBase64FromMediaMessage/" + cfg.whatsappInstanceId().strip();
        for (String requestBody : evolutionMediaRequestCandidates(providerMessageId.strip())) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(resolveDownloadTimeout())
                        .header("Content-Type", "application/json")
                        .header("apikey", cfg.whatsappApiKey().strip())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    continue;
                }
                Optional<DecodedEvolutionMedia> decoded = extractDecodedMediaFromEvolutionResponse(res.body());
                if (decoded.isEmpty()) {
                    continue;
                }
                byte[] bytes = java.util.Base64.getDecoder().decode(decoded.get().base64());
                if (bytes.length == 0 || bytes.length > MAX_AUDIO_BYTES) {
                    continue;
                }
                String contentType = decoded.get().mimeType() != null ? decoded.get().mimeType() : "audio/ogg";
                String filename = inferFilenameFromMime(contentType);
                LOG.info(
                        "[AXEZAP-STT] media decode via Evolution concluído | tenant={} bytes={} contentType={}",
                        tenantForLog,
                        bytes.length,
                        contentType);
                return Optional.of(new DownloadedAudio(bytes, contentType, filename));
            } catch (Exception e) {
                LOG.debug("[AXEZAP-STT] tentativa de media decode via Evolution falhou: {}", e.toString());
            }
        }
        LOG.warn(
                "[AXEZAP-STT] Evolution não retornou mídia decodificada para messageId={} tenant={}",
                providerMessageId,
                tenantForLog);
        return Optional.empty();
    }

    private static boolean isLikelyEncryptedEvolutionMedia(String mediaUrl, String mimeType) {
        String lowUrl = mediaUrl == null ? "" : mediaUrl.toLowerCase(Locale.ROOT);
        String lowMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        return lowUrl.endsWith(".enc")
                || lowUrl.contains("mmg.whatsapp.net")
                || lowMime.contains("application/octet-stream");
    }

    private Optional<DecodedEvolutionMedia> extractDecodedMediaFromEvolutionResponse(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String b64 = findFirstTextByKeys(root, "base64", "data", "audioBase64", "fileBase64").orElse(null);
            if (b64 == null || b64.isBlank()) {
                return Optional.empty();
            }
            String mime =
                    findFirstTextByKeys(root, "mimetype", "mimeType", "fileType", "contentType")
                            .orElse("audio/ogg");
            return Optional.of(new DecodedEvolutionMedia(stripBase64Prefix(b64), mime));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> findFirstTextByKeys(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isObject()) {
            for (String key : keys) {
                JsonNode val = node.get(key);
                if (val != null && val.isTextual() && !val.asText("").isBlank()) {
                    return Optional.of(val.asText());
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                Optional<String> nested = findFirstTextByKeys(entry.getValue(), keys);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> nested = findFirstTextByKeys(child, keys);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private static String stripBase64Prefix(String b64) {
        int idx = b64.indexOf("base64,");
        if (idx >= 0) {
            return b64.substring(idx + "base64,".length());
        }
        return b64.strip();
    }

    private static List<String> evolutionMediaRequestCandidates(String messageId) {
        return List.of(
                "{\"message\":{\"key\":{\"id\":\"" + messageId + "\"}}}",
                "{\"id\":\"" + messageId + "\"}",
                "{\"messageId\":\"" + messageId + "\"}");
    }

    private static String inferFilenameFromMime(String mime) {
        String low = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (low.contains("ogg") || low.contains("opus")) {
            return "audio.ogg";
        }
        if (low.contains("mpeg") || low.contains("mp3")) {
            return "audio.mp3";
        }
        if (low.contains("wav")) {
            return "audio.wav";
        }
        if (low.contains("webm")) {
            return "audio.webm";
        }
        return "audio.bin";
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private void logTranscriptionResult(AudioTranscriptionResult result) {
        String text = result.text() == null ? "" : result.text().strip();
        LOG.info("[AXEZAP-STT] Transcrição concluída: {}", truncate(text, 220));
    }

    private record DownloadedAudio(byte[] bytes, String contentType, String filename) {}

    private record DecodedEvolutionMedia(String base64, String mimeType) {}
}
