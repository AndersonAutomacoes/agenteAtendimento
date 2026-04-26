package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.domain.tenant.PlanFeature;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class FeatureAccessEvaluator {

    private final JdbcClient jdbcClient;
    private final Environment environment;

    public FeatureAccessEvaluator(JdbcClient jdbcClient, Environment environment) {
        this.jdbcClient = jdbcClient;
        this.environment = environment;
    }

    public boolean canAccess(ProfileLevel profileLevel, PlanFeature feature) {
        // Perfil interno com acesso global às funcionalidades do produto.
        if (profileLevel == ProfileLevel.COMERCIAL) {
            return true;
        }
        Optional<Boolean> dbDecision = readFromDatabase(profileLevel, feature);
        if (dbDecision.isPresent()) {
            return dbDecision.get();
        }
        Optional<Boolean> configDecision = readFromConfig(profileLevel, feature);
        if (configDecision.isPresent()) {
            return configDecision.get();
        }
        return defaultDecision(profileLevel, feature);
    }

    private Optional<Boolean> readFromDatabase(ProfileLevel profileLevel, PlanFeature feature) {
        try {
            return jdbcClient
                    .sql(
                            """
                            SELECT enabled
                            FROM tenant_plan_feature
                            WHERE profile_level = ? AND feature_key = ?
                            """)
                    .param(profileLevel.name())
                    .param(feature.name())
                    .query(Boolean.class)
                    .optional();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Boolean> readFromConfig(ProfileLevel profileLevel, PlanFeature feature) {
        String key = "cerebro.plan.features." + profileLevel.name() + "." + feature.name();
        String raw = environment.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Boolean.parseBoolean(raw.strip()));
    }

    private static boolean defaultDecision(ProfileLevel level, PlanFeature feature) {
        return switch (feature) {
            case DASHBOARD, APPOINTMENTS, ANALYTICS, ANALYTICS_EXPORT_CSV, KNOWLEDGE_BASE, MONITORING ->
                    level.meets(ProfileLevel.PRO);
            case ANALYTICS_EXPORT_PDF -> level.meets(ProfileLevel.ULTRA);
            case SETTINGS -> level.meets(ProfileLevel.BASIC);
        };
    }
}
