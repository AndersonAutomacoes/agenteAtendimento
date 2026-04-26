package com.atendimento.cerebro.application.dto;

/** Catálogo de serviços do tenant (agendamento / RAG). */
public record TenantServiceDto(Long id, String name, Integer durationMinutes, boolean active) {

    public TenantServiceDto {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
    }
}
