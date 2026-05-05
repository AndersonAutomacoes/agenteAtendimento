package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.port.out.EvolutionInstanceAdminPort;
import com.atendimento.cerebro.application.port.out.EvolutionInstanceMappingStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.util.Objects;
import java.util.Optional;

/**
 * Orquestra criação da instância Evolution, webhook público, gravação na config do tenant e vínculo para o webhook.
 */
public class EvolutionTenantProvisioningService {

    private final EvolutionInstanceAdminPort evolutionInstanceAdmin;
    private final EvolutionInstanceMappingStorePort evolutionInstanceMapping;
    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final String globalEvolutionBaseUrl;
    private final String globalEvolutionApiKey;
    private final String webhookPublicBaseUrl;

    public EvolutionTenantProvisioningService(
            EvolutionInstanceAdminPort evolutionInstanceAdmin,
            EvolutionInstanceMappingStorePort evolutionInstanceMapping,
            TenantConfigurationStorePort tenantConfigurationStore,
            String globalEvolutionBaseUrl,
            String globalEvolutionApiKey,
            String webhookPublicBaseUrl) {
        this.evolutionInstanceAdmin = Objects.requireNonNull(evolutionInstanceAdmin);
        this.evolutionInstanceMapping = Objects.requireNonNull(evolutionInstanceMapping);
        this.tenantConfigurationStore = Objects.requireNonNull(tenantConfigurationStore);
        this.globalEvolutionBaseUrl = globalEvolutionBaseUrl != null ? globalEvolutionBaseUrl.strip() : "";
        this.globalEvolutionApiKey = globalEvolutionApiKey != null ? globalEvolutionApiKey.strip() : "";
        this.webhookPublicBaseUrl = webhookPublicBaseUrl != null ? webhookPublicBaseUrl.strip() : "";
    }

    public record ProvisionOutcome(
            String instanceName,
            Optional<String> qrcodeBase64WithoutPrefix,
            Optional<String> warning) {}

    /**
     * Provisiona Evolution para o tenant: cria instância, webhook (URL pública obrigatória), atualiza
     * {@link TenantConfiguration} e o mapeamento instância → tenant.
     */
    public ProvisionOutcome provision(TenantId tenantId) {
        if (globalEvolutionBaseUrl.isEmpty() || globalEvolutionApiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Provisioning Evolution requer CEREBRO_WHATSAPP_EVOLUTION_BASE_URL e "
                            + "CEREBRO_WHATSAPP_EVOLUTION_API_KEY (ou valores equivalentes na configuração).");
        }
        if (webhookPublicBaseUrl.isEmpty()) {
            throw new IllegalStateException(
                    "Provisioning Evolution requer cerebro.whatsapp.evolution.webhook-public-base-url "
                            + "(URL pública HTTPS do cerebro onde a Evolution alcance POST /api/v1/whatsapp/webhook).");
        }
        String instanceName = EvolutionInstanceNameBuilder.fromTenantId(tenantId);
        TenantConfiguration base =
                tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));

        var created =
                evolutionInstanceAdmin.createWhatsappBaileysInstance(
                        trimSlash(globalEvolutionBaseUrl), globalEvolutionApiKey, instanceName, true);
        Optional<String> qrFromCreate =
                extractPlainBase64(created.qrcodeBase64().map(EvolutionTenantProvisioningService::normalizeBase64Payload));

        if (!created.success()) {
            if (hintInstanceExists(created.httpStatus(), created.rawResponse())) {
                qrFromCreate = qrFromCreate.or(() -> reconnectQr(instanceName));
            } else {
                String hint = truncate(created.rawResponse(), 240);
                throw new IllegalStateException(
                        "Evolution falhou ao criar instância HTTP " + created.httpStatus() + ": " + hint);
            }
        }

        String webhookTarget = trimSlash(webhookPublicBaseUrl) + "/api/v1/whatsapp/webhook";
        evolutionInstanceAdmin.setInstanceWebhook(
                trimSlash(globalEvolutionBaseUrl), globalEvolutionApiKey, instanceName, webhookTarget, true);

        Optional<String> qr = qrFromCreate.isPresent() ? qrFromCreate : reconnectQr(instanceName);

        TenantConfiguration evolved =
                new TenantConfiguration(
                        tenantId,
                        base.systemPrompt(),
                        WhatsAppProviderType.EVOLUTION,
                        tenantSaveKey(globalEvolutionApiKey, base),
                        instanceName,
                        tenantSaveUrl(globalEvolutionBaseUrl, base),
                        base.profileLevel(),
                        base.portalPasswordHash(),
                        base.googleCalendarId(),
                        base.establishmentName(),
                        base.businessAddress(),
                        base.businessMapsUrl(),
                        base.openingHours(),
                        base.businessContacts(),
                        base.businessFacilities(),
                        base.defaultAppointmentMinutes(),
                        base.billingCompliant(),
                        base.calendarAccessNotes(),
                        base.spreadsheetUrl(),
                        base.whatsappBusinessNumber());
        tenantConfigurationStore.upsert(evolved);
        evolutionInstanceMapping.upsert(tenantId, instanceName);
        evolutionInstanceMapping.updateConnectionState(instanceName, "PAIRING_PENDING");
        Optional<String> warn =
                webhookPublicBaseUrl.contains("localhost")
                        ? Optional.of(
                                "webhook-public-base-url aponta para localhost — a Evolution no Docker pode não conseguir chamar esse URL.")
                        : Optional.empty();
        return new ProvisionOutcome(instanceName, qr, warn);
    }

    /**
     * Ambiente configurado para criar instância Evolution automaticamente ao registar tenant interno.
     */
    public boolean isAutoProvisioningConfigured() {
        return !globalEvolutionBaseUrl.isEmpty()
                && !globalEvolutionApiKey.isEmpty()
                && !webhookPublicBaseUrl.isEmpty();
    }

    /** Solicita novo QR para instância já ligada ao tenant (portal). */
    public Optional<String> reconnectForTenant(TenantId tenantId) {
        TenantConfiguration cfg =
                tenantConfigurationStore.findByTenantId(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant"));
        if (cfg.whatsappProviderType() != WhatsAppProviderType.EVOLUTION) {
            throw new IllegalStateException("tenant não usa Evolution");
        }
        String instance = cfg.whatsappInstanceId();
        if (instance == null || instance.isBlank()) {
            throw new IllegalStateException("whatsapp_instance_id em falta");
        }
        // Igual ao envio outbound (EvolutionCredentials): override global primeiro. Evita 401 quando o BD
        // guardou chave/url antigas mas CEREBRO_WHATSAPP_EVOLUTION_* está correto.
        String base = evolutionHttpBaseUrl(cfg);
        String apiKey = evolutionHttpApiKey(cfg);
        return extractPlainBase64(
                evolutionInstanceAdmin
                        .connectAndFetchQrcodeBase64(trimSlash(base), apiKey, instance.strip())
                        .map(EvolutionTenantProvisioningService::normalizeBase64Payload));
    }

    /** URL para chamadas HTTP à Evolution: prioridade aos valores globais (Spring/env). */
    private String evolutionHttpBaseUrl(TenantConfiguration cfg) {
        if (globalEvolutionBaseUrl != null && !globalEvolutionBaseUrl.isBlank()) {
            return globalEvolutionBaseUrl;
        }
        String t = cfg.whatsappBaseUrl() != null ? cfg.whatsappBaseUrl().strip() : "";
        return t;
    }

    /** Chave apikey Evolution: prioridade aos valores globais (Spring/env). */
    private String evolutionHttpApiKey(TenantConfiguration cfg) {
        if (globalEvolutionApiKey != null && !globalEvolutionApiKey.isBlank()) {
            return globalEvolutionApiKey;
        }
        String t = cfg.whatsappApiKey() != null ? cfg.whatsappApiKey().strip() : "";
        return t;
    }

    private Optional<String> reconnectQr(String instanceName) {
        return extractPlainBase64(
                evolutionInstanceAdmin
                        .connectAndFetchQrcodeBase64(
                                trimSlash(globalEvolutionBaseUrl),
                                globalEvolutionApiKey,
                                instanceName.strip())
                        .map(EvolutionTenantProvisioningService::normalizeBase64Payload));
    }

    private static boolean hintInstanceExists(int status, String body) {
        if (status == 409) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String b = body.toLowerCase();
        return b.contains("already exists") || b.contains(" já ") || b.contains("duplicate");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /** Guarda espaço quando a chave global já cobre (URL interna igual à global por defeito em Docker). */
    private static String tenantSaveUrl(String effectiveGlobal, TenantConfiguration cfg) {
        String t = cfg.whatsappBaseUrl() != null ? cfg.whatsappBaseUrl().strip() : "";
        if (!t.isEmpty()) {
            return t;
        }
        return effectiveGlobal;
    }

    private static String tenantSaveKey(String effectiveGlobalKey, TenantConfiguration cfg) {
        String t = cfg.whatsappApiKey() != null ? cfg.whatsappApiKey().strip() : "";
        if (!t.isEmpty()) {
            return t;
        }
        return effectiveGlobalKey;
    }

    /**
     * Resposta Evolution pode incluir apenas prefix data-uri ou base64 nu; extrai apenas caracteres úteis ou retorna empty
     * se parecer já ser image/png data URI só para uso directo em email.
     */
    private static Optional<String> extractPlainBase64(Optional<String> raw) {
        return raw.flatMap(EvolutionTenantProvisioningService::normalizeToPlainBase64);
    }

    private static Optional<String> normalizeToPlainBase64(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        String p = payload.strip();
        if (p.startsWith("data:image")) {
            int comma = p.indexOf(',');
            if (comma > 0 && comma < p.length() - 1) {
                return Optional.of(p.substring(comma + 1).strip());
            }
            return Optional.empty();
        }
        return Optional.of(p);
    }

    private static String normalizeBase64Payload(String payload) {
        if (payload == null) {
            return "";
        }
        return payload.strip();
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String u = url.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Gera nome de instância seguro Evolution + único por tenant no mesmo servidor. */
    public static final class EvolutionInstanceNameBuilder {
        private EvolutionInstanceNameBuilder() {}

        public static String fromTenantId(TenantId tenantId) {
            String normalized =
                    tenantId.value().trim().replaceAll("[^a-zA-Z0-9_-]", "_");
            if (normalized.length() > 48) {
                normalized = normalized.substring(0, 48);
            }
            if (normalized.isBlank()) {
                normalized = "tenant";
            }
            return "evo-" + normalized;
        }
    }
}
