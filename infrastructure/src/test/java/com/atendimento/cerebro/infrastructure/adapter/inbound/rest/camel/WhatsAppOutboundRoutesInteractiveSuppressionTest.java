package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveKind;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.dto.WhatsAppInteractiveRow;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import com.atendimento.cerebro.infrastructure.whatsapp.EvolutionInteractiveMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhatsAppOutboundRoutesInteractiveSuppressionTest {

    @Test
    void shouldSuppressPlainTextWhenInteractive_trueForEvolutionWithSlotTimes() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply("t", "d", List.of("09:00", "09:30"));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.LIST))
                .isTrue();
    }

    @Test
    void shouldSuppress_falseInTextModeWithSlots() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply("t", "d", List.of("09:00", "09:30"));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.TEXT))
                .isFalse();
    }

    @Test
    void shouldSuppress_trueForServiceCatalogInListMode() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.SERVICES,
                        "S",
                        "D",
                        List.of(),
                        "",
                        null,
                        "Serviços",
                        "",
                        List.of(new WhatsAppInteractiveRow("service_1", "A", "")));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.LIST))
                .isTrue();
    }

    @Test
    void shouldSuppress_trueForServiceCatalogEvenInTextMode() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.SERVICES,
                        "S",
                        "D",
                        List.of(),
                        "",
                        null,
                        "Serviços",
                        "",
                        List.of(new WhatsAppInteractiveRow("service_1", "A", "")));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.TEXT))
                .isTrue();
    }

    @Test
    void shouldSuppressPlainTextWhenInteractive_falseForConfirmationKind() {
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION,
                                WhatsAppInteractiveReply.forConfirmationActions(),
                                EvolutionInteractiveMode.LIST))
                .isFalse();
    }

    @Test
    void shouldSuppress_trueForAppointmentPickListInListMode() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.APPOINTMENT_LIST,
                        "A",
                        "D",
                        List.of(),
                        "",
                        null,
                        "Ver",
                        "",
                        List.of(new WhatsAppInteractiveRow("pick_appt_1", "Opção 1", "")));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.LIST))
                .isTrue();
    }

    @Test
    void shouldSuppress_trueForAppointmentPickListEvenInTextMode() {
        WhatsAppInteractiveReply interactive =
                new WhatsAppInteractiveReply(
                        WhatsAppInteractiveKind.APPOINTMENT_LIST,
                        "A",
                        "D",
                        List.of(),
                        "",
                        null,
                        "Ver",
                        "",
                        List.of(new WhatsAppInteractiveRow("pick_appt_1", "Opção 1", "")));
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, interactive, EvolutionInteractiveMode.TEXT))
                .isTrue();
    }

    @Test
    void shouldSuppressPlainTextWhenInteractive_falseForNonEvolutionOrNoSlots() {
        WhatsAppInteractiveReply emptyInteractive =
                new WhatsAppInteractiveReply("t", "d", List.of());
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.EVOLUTION, emptyInteractive, EvolutionInteractiveMode.LIST))
                .isFalse();
        assertThat(
                        WhatsAppOutboundRoutes.shouldSuppressPlainTextWhenInteractive(
                                WhatsAppProviderType.META,
                                new WhatsAppInteractiveReply("t", "d", List.of("09:00")),
                                EvolutionInteractiveMode.LIST))
                .isFalse();
    }
}
