package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SystemPromptPlaceholdersTest {

    @Test
    void replacesPlaceholdersUsingZone() {
        ZoneId z = ZoneId.of("America/Bahia");
        LocalDate today = LocalDate.now(z);
        LocalDate tomorrow = today.plusDays(1);
        String in = "Hoje {{current_date}} amanhã {{tomorrow_date}}";
        String out = SystemPromptPlaceholders.apply(in, z);
        assertThat(out)
                .doesNotContain("{{")
                .contains(String.valueOf(today.getYear()))
                .contains(today.toString())
                .contains(tomorrow.toString());
    }

    @Test
    void noopWhenNoPlaceholders() {
        assertThat(SystemPromptPlaceholders.apply("Só texto", ZoneId.of("UTC"))).isEqualTo("Só texto");
    }
}
