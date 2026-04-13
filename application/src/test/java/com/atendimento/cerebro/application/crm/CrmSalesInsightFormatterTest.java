package com.atendimento.cerebro.application.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrmSalesInsightFormatterTest {

    @Test
    void budget_no_conversion_suggests_checkup() {
        var c =
                new CrmCustomerRecord(
                        UUID.randomUUID(),
                        "t1",
                        "wa-5511",
                        "5511",
                        "João",
                        null,
                        Instant.parse("2025-01-01T12:00:00Z"),
                        0,
                        null,
                        "Orçamento",
                        "Orçamento",
                        74,
                        false,
                        "PENDING_LEAD",
                        Instant.now());
        assertThat(CrmSalesInsightFormatter.buildInsight(c)).contains("check-up");
    }
}
