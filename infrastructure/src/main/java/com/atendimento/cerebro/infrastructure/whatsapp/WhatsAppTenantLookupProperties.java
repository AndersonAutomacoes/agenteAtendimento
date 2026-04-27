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

    /**
     * Nome da instância Evolution (ex.: valor de {@code instance} no webhook) → tenantId.
     */
    private Map<String, String> instanceTenants = new HashMap<>();

    public Map<String, String> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, String> tenants) {
        this.tenants = tenants != null ? tenants : new HashMap<>();
    }

    public Map<String, String> getInstanceTenants() {
        return instanceTenants;
    }

    public void setInstanceTenants(Map<String, String> instanceTenants) {
        this.instanceTenants = instanceTenants != null ? instanceTenants : new HashMap<>();
    }
}
