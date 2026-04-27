package com.atendimento.cerebro.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.multitenancy")
public class CerebroMultitenancyProperties {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private boolean enabled = false;

    /** Header HTTP opcional (Evolution webhook ou proxy) com o tenantId/schema lógico. */
    private String headerName = "X-AxeZap-Tenant";

    /** Schema quando não há tenant em contexto (Flyway, jobs, pedidos sem webhook). */
    private String defaultSchema = "public";

    /**
     * Mapeamento tenantId lógico → nome do schema físico. Se a chave não existir, usa-se o próprio tenantId como nome
     * de schema (desde que seja um identificador SQL seguro).
     */
    private Map<String, String> tenantSchemas = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName != null ? headerName : "X-AxeZap-Tenant";
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema != null ? defaultSchema : "public";
    }

    public Map<String, String> getTenantSchemas() {
        return tenantSchemas;
    }

    public void setTenantSchemas(Map<String, String> tenantSchemas) {
        this.tenantSchemas = tenantSchemas != null ? tenantSchemas : new HashMap<>();
    }

    public String resolveSchema(String tenantId) {
        if (!enabled) {
            return sanitizeOrDefault(defaultSchema);
        }
        if (tenantId == null || tenantId.isBlank()) {
            return sanitizeOrDefault(defaultSchema);
        }
        String key = tenantId.strip();
        String mapped = tenantSchemas.get(key);
        if (mapped != null && !mapped.isBlank()) {
            return sanitizeOrDefault(mapped.strip());
        }
        return sanitizeOrDefault(key);
    }

    private String sanitizeOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return "public";
        }
        if (!SAFE_SCHEMA.matcher(raw).matches()) {
            return "public";
        }
        return raw;
    }
}
