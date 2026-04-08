package com.atendimento.cerebro.infrastructure.whatsapp;

import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class WhatsAppTenantLookupAdapter implements WhatsAppTenantLookupPort {

    private final Map<String, String> normalizedPhoneToTenantId;

    public WhatsAppTenantLookupAdapter(WhatsAppTenantLookupProperties properties) {
        this.normalizedPhoneToTenantId = new HashMap<>();
        for (Map.Entry<String, String> e : properties.getTenants().entrySet()) {
            String key = normalizeDigits(e.getKey());
            if (!key.isEmpty() && e.getValue() != null && !e.getValue().isBlank()) {
                normalizedPhoneToTenantId.put(key, e.getValue().strip());
            }
        }
    }

    @Override
    public Optional<TenantId> findTenantIdByWhatsAppNumber(String normalizedPhoneDigits) {
        if (normalizedPhoneDigits == null || normalizedPhoneDigits.isBlank()) {
            return Optional.empty();
        }
        String key = normalizeDigits(normalizedPhoneDigits);
        String tenant = normalizedPhoneToTenantId.get(key);
        return tenant != null ? Optional.of(new TenantId(tenant)) : Optional.empty();
    }

    private static String normalizeDigits(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
