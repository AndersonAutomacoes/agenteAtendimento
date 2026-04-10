package com.atendimento.cerebro.infrastructure.adapter.out.dedupe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class InboundWhatsAppDeduperTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private InboundWhatsAppDeduper deduper;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        deduper = new InboundWhatsAppDeduper(redis, true, 3600, "wa:test");
    }

    @Test
    void tryClaim_blankMessageId_alwaysTrue_noRedis() {
        InboundWhatsAppDeduper d = new InboundWhatsAppDeduper(redis, true, 3600, "wa:test");
        assertThat(d.tryClaimInboundMessage("t1", null)).isTrue();
        assertThat(d.tryClaimInboundMessage("t1", "   ")).isTrue();
        verifyNoInteractions(redis);
    }

    @Test
    void tryClaim_redisSetNxTrue_firstClaimWins() {
        when(valueOps.setIfAbsent(eq("wa:test:tenant-a:msg-1"), eq("1"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);
        assertThat(deduper.tryClaimInboundMessage("tenant-a", "msg-1")).isTrue();
        assertThat(deduper.tryClaimInboundMessage("tenant-a", "msg-1")).isFalse();
    }

    @Test
    void featureDisabled_skipsRedis() {
        InboundWhatsAppDeduper off = new InboundWhatsAppDeduper(redis, false, 3600, "wa:test");
        assertThat(off.tryClaimInboundMessage("tenant-a", "msg-1")).isTrue();
        assertThat(off.tryClaimInboundMessage("tenant-a", "msg-1")).isTrue();
    }

    @Test
    void noRedisTemplate_alwaysTrue() {
        InboundWhatsAppDeduper local = new InboundWhatsAppDeduper(null, true, 3600, "wa:test");
        assertThat(local.tryClaimInboundMessage("tenant-a", "msg-1")).isTrue();
    }
}
