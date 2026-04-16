package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppOutboundRoutesSanitizeTest {

    @Test
    void sanitizeOutboundBody_stripsSlotOptionsLikeInternalTags() {
        assertThat(WhatsAppOutboundRoutes.sanitizeOutboundBody("Escolha [slot_options:09:00,10:00] um horário"))
                .doesNotContain("[")
                .doesNotContain("]")
                .contains("Escolha")
                .contains("horário");
    }

    @Test
    void sanitizeOutboundBody_emptySafe() {
        assertThat(WhatsAppOutboundRoutes.sanitizeOutboundBody(null)).isEmpty();
        assertThat(WhatsAppOutboundRoutes.sanitizeOutboundBody("")).isEmpty();
    }

    @Test
    void sanitizeOutboundBody_stripsGoogleCalendarUrls() {
        assertThat(
                        WhatsAppOutboundRoutes.sanitizeOutboundBody(
                                "Ok https://www.google.com/calendar/event?eid=x fim"))
                .doesNotContain("google.com/calendar");
    }
}
