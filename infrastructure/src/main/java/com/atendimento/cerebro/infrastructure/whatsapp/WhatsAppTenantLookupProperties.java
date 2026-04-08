package com.atendimento.cerebro.infrastructure.whatsapp;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cerebro.whatsapp")
public class WhatsAppTenantLookupProperties {

    /**
     * Número WhatsApp (normalizado para só dígitos na chave) → tenantId.
     */
    private Map<String, String> tenants = new HashMap<>();

    public Map<String, String> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, String> tenants) {
        this.tenants = tenants != null ? tenants : new HashMap<>();
    }
}
