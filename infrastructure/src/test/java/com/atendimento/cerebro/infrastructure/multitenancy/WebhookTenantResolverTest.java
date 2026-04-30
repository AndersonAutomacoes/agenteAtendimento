package com.atendimento.cerebro.infrastructure.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.EvolutionInstanceMappingStorePort;
import com.atendimento.cerebro.application.port.out.WhatsAppTenantLookupPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.whatsapp.WhatsAppTenantLookupProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookTenantResolverTest {

    @Test
    void evolutionInstance_prefersDbOverYamlWhenBothDefined() {
        EvolutionInstanceMappingStorePort db =
                new EvolutionInstanceMappingStorePort() {
                    @Override
                    public Optional<TenantId> findTenantIdByEvolutionInstanceName(String evolutionInstanceName) {
                        return Optional.of(new TenantId("tenant-db"));
                    }

                    @Override
                    public void upsert(TenantId tenantId, String evolutionInstanceName) {}

                    @Override
                    public void updateConnectionState(String evolutionInstanceName, String connectionState) {}
                };

        WhatsAppTenantLookupProperties props = new WhatsAppTenantLookupProperties();
        Map<String, String> yaml = new HashMap<>();
        yaml.put("inst-x", "tenant-yaml");
        props.setInstanceTenants(yaml);

        WhatsAppTenantLookupPort phoneLookup = digits -> Optional.empty();

        WebhookTenantResolver resolver = new WebhookTenantResolver(phoneLookup, props, db);

        Optional<TenantId> t =
                resolver.resolve(Optional.empty(), Optional.of("inst-x"), "", Optional.empty());
        assertThat(t).contains(new TenantId("tenant-db"));
    }

    @Test
    void yamlFallback_whenDbMiss() {
        EvolutionInstanceMappingStorePort db =
                new EvolutionInstanceMappingStorePort() {
                    @Override
                    public Optional<TenantId> findTenantIdByEvolutionInstanceName(String evolutionInstanceName) {
                        return Optional.empty();
                    }

                    @Override
                    public void upsert(TenantId tenantId, String evolutionInstanceName) {}

                    @Override
                    public void updateConnectionState(String evolutionInstanceName, String connectionState) {}
                };

        WhatsAppTenantLookupProperties props = new WhatsAppTenantLookupProperties();
        props.setInstanceTenants(Map.of("inst-z", "tenant-y"));

        WhatsAppTenantLookupPort phoneLookup = digits -> Optional.empty();

        WebhookTenantResolver resolver = new WebhookTenantResolver(phoneLookup, props, db);

        Optional<TenantId> t =
                resolver.resolve(Optional.empty(), Optional.of("inst-z"), "", Optional.empty());
        assertThat(t).contains(new TenantId("tenant-y"));
    }
}
