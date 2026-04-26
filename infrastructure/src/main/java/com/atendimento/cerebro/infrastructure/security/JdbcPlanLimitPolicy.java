package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.application.port.out.PlanLimitPolicyPort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcPlanLimitPolicy implements PlanLimitPolicyPort {

    private final JdbcClient jdbcClient;

    public JdbcPlanLimitPolicy(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Integer> maxAppointmentsPerMonth(ProfileLevel profileLevel) {
        try {
            return jdbcClient
                    .sql(
                            """
                            SELECT max_appointments_per_month
                            FROM tenant_plan_limit
                            WHERE profile_level = ?
                            """)
                    .param(profileLevel.name())
                    .query(Integer.class)
                    .optional();
        } catch (RuntimeException ignored) {
            // fallback seguro se tabela/migração ainda não aplicada
            return defaultLimit(profileLevel);
        }
    }

    private static Optional<Integer> defaultLimit(ProfileLevel profileLevel) {
        return switch (profileLevel) {
            case BASIC -> Optional.of(50);
            case PRO -> Optional.of(300);
            case ULTRA, COMERCIAL -> Optional.empty();
        };
    }
}
