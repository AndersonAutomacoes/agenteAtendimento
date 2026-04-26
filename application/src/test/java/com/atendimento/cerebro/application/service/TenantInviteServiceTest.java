package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.atendimento.cerebro.application.port.out.InviteEmailSenderPort;
import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TenantInviteServiceTest {

    private TenantInviteStorePort tenantInviteStore;
    private InviteEmailSenderPort inviteEmailSenderPort;
    private TenantInviteService tenantInviteService;

    @BeforeEach
    void setUp() {
        tenantInviteStore = Mockito.mock(TenantInviteStorePort.class);
        inviteEmailSenderPort = Mockito.mock(InviteEmailSenderPort.class);
        tenantInviteService = new TenantInviteService(tenantInviteStore, inviteEmailSenderPort);
    }

    @Test
    void createInviteAndSendEmail_sendsCommandWithInviteCode() {
        String code =
                tenantInviteService.createInviteAndSendEmail(
                        new TenantId("tenant-a"),
                        5,
                        Instant.parse("2026-05-01T12:00:00Z"),
                        "cliente@empresa.com",
                        "Oficina Centro");

        assertThat(code).isNotBlank();
        verify(tenantInviteStore)
                .insertNewInvite(any(UUID.class), any(TenantId.class), any(String.class), any(Integer.class), any(Instant.class));
        verify(inviteEmailSenderPort).sendInviteEmail(any(InviteEmailSenderPort.InviteEmailCommand.class));
    }

    @Test
    void createInviteAndSendEmail_rejectsInvalidEmail() {
        assertThatThrownBy(
                        () ->
                                tenantInviteService.createInviteAndSendEmail(
                                        new TenantId("tenant-a"),
                                        5,
                                        null,
                                        "email-invalido",
                                        "Oficina Centro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inviteEmail inválido");
    }
}
