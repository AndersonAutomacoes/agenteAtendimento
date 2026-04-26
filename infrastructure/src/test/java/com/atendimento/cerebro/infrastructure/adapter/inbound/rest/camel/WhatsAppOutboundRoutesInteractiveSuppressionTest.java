package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhatsAppOutboundRoutesInteractiveSuppressionTest {

    @Test
    void shouldSuppressPlainTextWhenInteractive_trueForEvolutionWithSlotTimes() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply("t", "d", List.of("09:00", "09:30"));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive))
                .isTrue();
    }

    @Test
    void shouldSuppressPlainTextWhenInteractive_falseForNonEvolutionOrNoSlots() {
        WhatsAppInteractiveReply emptyInteractive =
                new WhatsAppInteractiveReply("t", "d", List.of());
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, emptyInteractive))
                .isFalse();
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.META,
                                new WhatsAppInteractiveReply("t", "d", List.of("09:00"))))
                .isFalse();
    }
}
