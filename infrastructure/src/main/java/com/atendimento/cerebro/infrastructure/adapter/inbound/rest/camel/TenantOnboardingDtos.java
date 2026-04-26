package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.TenantServiceDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class TenantOnboardingDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PortalUserJson(String id, String firebaseUid, String profileLevel) {}

    public record PortalUsersResponse(List<PortalUserJson> users) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateInviteRequest(Integer maxUses, String inviteEmail) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateInviteResponse(
            String inviteCode,
            String message,
            String emailHint,
            String emailSentTo) {}

    public record TenantServiceWriteItem(
            String name, Integer durationMinutes, Boolean active) {}

    public record TenantServicesWriteRequest(List<TenantServiceWriteItem> services) {}

    public record TenantServicesListResponse(List<TenantServiceDto> services) {}

    private TenantOnboardingDtos() {}
}
