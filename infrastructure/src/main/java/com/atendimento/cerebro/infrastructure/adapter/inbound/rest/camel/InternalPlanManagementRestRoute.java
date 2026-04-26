package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.domain.tenant.PlanFeature;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Order(103)
public class InternalPlanManagementRestRoute extends RouteBuilder {

    private final JdbcClient jdbcClient;

    public InternalPlanManagementRestRoute(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void configure() {
        rest("/v1/internal/plans/config")
                .get()
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .outType(PlanConfigResponse.class)
                .to("direct:internalPlansConfigGet")
                .put()
                .consumes(MediaType.APPLICATION_JSON_VALUE)
                .produces(MediaType.APPLICATION_JSON_VALUE)
                .type(PlanConfigWriteRequest.class)
                .to("direct:internalPlansConfigPut");

        from("direct:internalPlansConfigGet")
                .routeId("internalPlansConfigGet")
                .process(this::handleGet);
        from("direct:internalPlansConfigPut")
                .routeId("internalPlansConfigPut")
                .process(this::handlePut);
    }

    private void handleGet(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        List<PlanFeatureRow> features =
                jdbcClient
                        .sql(
                                """
                                SELECT profile_level, feature_key, enabled
                                FROM tenant_plan_feature
                                ORDER BY profile_level, feature_key
                                """)
                        .query(
                                (rs, i) ->
                                        new PlanFeatureRow(
                                                rs.getString("profile_level"),
                                                rs.getString("feature_key"),
                                                rs.getBoolean("enabled")))
                        .list();

        List<PlanLimitRow> limits =
                jdbcClient
                        .sql(
                                """
                                SELECT profile_level, max_appointments_per_month
                                FROM tenant_plan_limit
                                ORDER BY profile_level
                                """)
                        .query(
                                (rs, i) ->
                                        new PlanLimitRow(
                                                rs.getString("profile_level"),
                                                (Integer) rs.getObject("max_appointments_per_month", Integer.class)))
                        .list();

        exchange.getMessage().setBody(new PlanConfigResponse(features, limits));
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
    }

    private void handlePut(Exchange exchange) {
        if (!isCommercialOperator()) {
            exchange.getIn().setBody(new IngestErrorResponse("perfil insuficiente"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.FORBIDDEN.value());
            return;
        }
        PlanConfigWriteRequest body = exchange.getIn().getBody(PlanConfigWriteRequest.class);
        if (body == null) {
            exchange.getIn().setBody(new IngestErrorResponse("payload é obrigatório"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
            return;
        }
        try {
            List<PlanFeatureWriteItem> featureItems = body.features() != null ? body.features() : List.of();
            List<PlanLimitWriteItem> limitItems = body.limits() != null ? body.limits() : List.of();

            for (PlanFeatureWriteItem it : featureItems) {
                ProfileLevel p = ProfileLevel.valueOf(it.profileLevel().strip().toUpperCase());
                PlanFeature f = PlanFeature.valueOf(it.featureKey().strip().toUpperCase());
                jdbcClient
                        .sql(
                                """
                                INSERT INTO tenant_plan_feature (profile_level, feature_key, enabled, updated_at)
                                VALUES (?, ?, ?, NOW())
                                ON CONFLICT (profile_level, feature_key) DO UPDATE SET
                                    enabled = EXCLUDED.enabled,
                                    updated_at = NOW()
                                """)
                        .param(p.name())
                        .param(f.name())
                        .param(it.enabled())
                        .update();
            }

            for (PlanLimitWriteItem it : limitItems) {
                ProfileLevel p = ProfileLevel.valueOf(it.profileLevel().strip().toUpperCase());
                Integer max = it.maxAppointmentsPerMonth();
                if (max != null && max <= 0) {
                    max = null;
                }
                jdbcClient
                        .sql(
                                """
                                INSERT INTO tenant_plan_limit (profile_level, max_appointments_per_month, updated_at)
                                VALUES (?, ?, NOW())
                                ON CONFLICT (profile_level) DO UPDATE SET
                                    max_appointments_per_month = EXCLUDED.max_appointments_per_month,
                                    updated_at = NOW()
                                """)
                        .param(p.name())
                        .param(max)
                        .update();
            }

            // devolve estado consolidado pós-gravação
            List<PlanFeatureRow> outFeatures = new ArrayList<>();
            for (ProfileLevel p : ProfileLevel.values()) {
                for (PlanFeature f : PlanFeature.values()) {
                    Boolean enabled =
                            jdbcClient
                                    .sql(
                                            """
                                            SELECT enabled FROM tenant_plan_feature
                                            WHERE profile_level = ? AND feature_key = ?
                                            """)
                                    .param(p.name())
                                    .param(f.name())
                                    .query(Boolean.class)
                                    .optional()
                                    .orElse(false);
                    outFeatures.add(new PlanFeatureRow(p.name(), f.name(), enabled));
                }
            }
            outFeatures.sort(Comparator.comparing(PlanFeatureRow::profileLevel).thenComparing(PlanFeatureRow::featureKey));

            List<PlanLimitRow> outLimits =
                    jdbcClient
                            .sql(
                                    """
                                    SELECT profile_level, max_appointments_per_month
                                    FROM tenant_plan_limit
                                    ORDER BY profile_level
                                    """)
                            .query(
                                    (rs, i) ->
                                            new PlanLimitRow(
                                                    rs.getString("profile_level"),
                                                    (Integer) rs.getObject("max_appointments_per_month", Integer.class)))
                            .list();

            exchange.getMessage().setBody(new PlanConfigResponse(outFeatures, outLimits));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.OK.value());
        } catch (IllegalArgumentException e) {
            exchange.getIn().setBody(new IngestErrorResponse("profileLevel/featureKey inválido"));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, HttpStatus.BAD_REQUEST.value());
        }
    }

    private static boolean isCommercialOperator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof PortalAuthenticationToken pat)) {
            return false;
        }
        return pat.getProfileLevel() == ProfileLevel.COMERCIAL;
    }

    public record PlanFeatureRow(String profileLevel, String featureKey, boolean enabled) {}

    public record PlanLimitRow(String profileLevel, Integer maxAppointmentsPerMonth) {}

    public record PlanConfigResponse(List<PlanFeatureRow> features, List<PlanLimitRow> limits) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanFeatureWriteItem(String profileLevel, String featureKey, boolean enabled) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanLimitWriteItem(String profileLevel, Integer maxAppointmentsPerMonth) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanConfigWriteRequest(List<PlanFeatureWriteItem> features, List<PlanLimitWriteItem> limits) {}
}
