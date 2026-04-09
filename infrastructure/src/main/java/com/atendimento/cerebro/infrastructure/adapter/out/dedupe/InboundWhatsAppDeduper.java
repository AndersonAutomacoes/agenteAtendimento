package com.atendimento.cerebro.infrastructure.adapter.out.dedupe;

import com.atendimento.cerebro.application.port.out.InboundWhatsAppDeduperPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SET NX + TTL quando Redis está disponível; sem Redis (ex.: testes) ou com feature desligada → sempre {@code true}.
 * Falha do Redis em runtime → fail-open ({@code true}).
 */
@Component
public class InboundWhatsAppDeduper implements InboundWhatsAppDeduperPort {

    private static final Logger LOG = LoggerFactory.getLogger(InboundWhatsAppDeduper.class);

    private final StringRedisTemplate redis;
    private final boolean featureEnabled;
    private final Duration ttl;
    private final String keyPrefix;

    public InboundWhatsAppDeduper(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Value("${cerebro.whatsapp.inbound-dedupe.enabled:true}") boolean featureEnabled,
            @Value("${cerebro.whatsapp.inbound-dedupe.ttl-seconds:172800}") long ttlSeconds,
            @Value("${cerebro.whatsapp.inbound-dedupe.key-prefix:wa:in}") String keyPrefix) {
        this.redis = redisTemplate;
        this.featureEnabled = featureEnabled;
        this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
        this.keyPrefix = keyPrefix != null && !keyPrefix.isBlank() ? keyPrefix.strip() : "wa:in";
    }

    @Override
    public boolean tryClaimInboundMessage(String tenantId, String providerMessageId) {
        if (!featureEnabled
                || providerMessageId == null
                || providerMessageId.isBlank()
                || tenantId == null
                || tenantId.isBlank()) {
            return true;
        }
        if (redis == null) {
            return true;
        }
        String key = keyPrefix + ":" + tenantId.strip() + ":" + providerMessageId.strip();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            LOG.warn(
                    "Redis inbound dedupe indisponível (fail-open); tenant={} msgIdPrefix={}…",
                    tenantId,
                    providerMessageId.length() > 8 ? providerMessageId.substring(0, 8) : providerMessageId,
                    e);
            return true;
        }
    }
}
