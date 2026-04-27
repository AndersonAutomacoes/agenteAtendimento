package com.atendimento.cerebro.infrastructure.multitenancy;

import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.whatsapp.WhatsAppTenantLookupProperties;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Resolve o tenant do webhook: header HTTP (opcional), nome de instância Evolution (YAML), depois telefone.
 */
@Component
public class WebhookTenantResolver {

    private static final Pattern SAFE_TENANT = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final WhatsAppTenantLookupPort phoneLookup;
    private final WhatsAppTenantLookupProperties whatsAppProps;

    public WebhookTenantResolver(
            WhatsAppTenantLookupPort phoneLookup, WhatsAppTenantLookupProperties whatsAppProps) {
        this.phoneLookup = phoneLookup;
        this.whatsAppProps = whatsAppProps;
    }

    public Optional<TenantId> resolve(
            Optional<String> headerTenantHint,
            Optional<String> evolutionInstanceName,
            String fromDigits,
            Optional<String> evolutionLineDigits) {

        if (headerTenantHint.isPresent() && !headerTenantHint.get().isBlank()) {
            Optional<TenantId> fromHeader = parseHeaderTenant(headerTenantHint.get());
            if (fromHeader.isPresent()) {
                return fromHeader;
            }
        }

        if (evolutionInstanceName.isPresent() && !evolutionInstanceName.get().isBlank()) {
            String key = evolutionInstanceName.get().strip();
            String mapped = whatsAppProps.getInstanceTenants().get(key);
            if (mapped != null && !mapped.isBlank()) {
                return Optional.of(new TenantId(mapped.strip()));
            }
        }

        var tenant = phoneLookup.findTenantIdByWhatsAppNumber(fromDigits);
        if (tenant.isEmpty() && evolutionLineDigits.isPresent() && !evolutionLineDigits.get().isBlank()) {
            tenant = phoneLookup.findTenantIdByWhatsAppNumber(evolutionLineDigits.get());
        }
        return tenant;
    }

    private Optional<TenantId> parseHeaderTenant(String raw) {
        String s = raw.strip();
        if (s.isEmpty() || !SAFE_TENANT.matcher(s).matches()) {
            return Optional.empty();
        }
        return Optional.of(new TenantId(s));
    }
}
