package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceResolutionTest {

    private static final TenantId TENANT = new TenantId("t1");

    @Mock private AppointmentService appointmentService;

    @Test
    void resolveForCreate_prefersExplicitUserCatalogMention_afterLastServiceCatalog() {
        List<Message> history =
                List.of(
                        Message.assistantMessage(
                                "Opcoes\n\n[service_option_map:1=Alinhamento 3D|2=Sangria de Freio|3=B]"),
                        Message.userMessage("Sangria de Freio"));
        when(appointmentService.isServiceInTenantCatalog(eq(TENANT), eq("Sangria de Freio")))
                .thenReturn(true);

        Optional<String> out =
                SchedulingServiceResolution.resolveForCreateOrConfirm(
                        TENANT, history, null, appointmentService);

        assertThat(out).hasValue("Sangria de Freio");
    }

    @Test
    void resolveForCreate_readsServiceFromDraftConfirmation_prose_evenWithoutSelectedToken() {
        List<Message> history =
                List.of(
                        Message.assistantMessage(
                                "Perfeito! O serviço *Alinhamento 3D* para *06/05/2026* às *09:30*. "
                                        + "Posso confirmar o agendamento?\n\n"
                                        + "[scheduling_draft:2026-05-06|09:30]"));

        when(appointmentService.isServiceInTenantCatalog(eq(TENANT), eq("Alinhamento 3D"))).thenReturn(true);

        Optional<String> out =
                SchedulingServiceResolution.resolveForCreateOrConfirm(
                        TENANT, history, null, appointmentService);

        assertThat(out).hasValue("Alinhamento 3D");
    }

    @Test
    void resolveForSlotCardTitle_usesSameFlow_asCreate() {
        List<Message> history =
                List.of(
                        Message.assistantMessage(
                                "Opcoes\n\n[service_option_map:1=Alinhamento 3D|2=Sangria de Freio]"),
                        Message.userMessage("Sangria de Freio"));
        when(appointmentService.isServiceInTenantCatalog(eq(TENANT), eq("Sangria de Freio")))
                .thenReturn(true);

        Optional<String> out =
                SchedulingServiceResolution.resolveForSlotCardTitle(
                        TENANT, history, "amanhã", appointmentService);

        assertThat(out).hasValue("Sangria de Freio");
    }
}
