package com.atendimento.cerebro.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Autenticação de integração para rotas Analytics: {@link IntegrationTestFirebaseTokenVerifier}
 * (token {@code Bearer integration:&lt;tenantId&gt;}) + dados em {@code portal_user} via SQL.
 * <p>
 * Equivalente a {@code @Import(IntegrationTestFirebaseConfiguration.class)} e
 * {@code @Sql("/integration-test/portal-users-analytics.sql")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(IntegrationTestFirebaseConfiguration.class)
@Sql(scripts = "/integration-test/portal-users-analytics.sql")
public @interface AnalyticsIntegrationAuth {}
