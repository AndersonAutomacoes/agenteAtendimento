package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.infrastructure.multitenancy.TenantSchemaRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(CerebroMultitenancyProperties.class)
public class MultitenancyDataSourceConfiguration {

    @Bean(name = "cerebroRawDataSource")
    public HikariDataSource cerebroRawDataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("cerebroRawDataSource") HikariDataSource raw,
            CerebroMultitenancyProperties multitenancyProperties) {
        if (!multitenancyProperties.isEnabled()) {
            return raw;
        }
        return new TenantSchemaRoutingDataSource(raw, multitenancyProperties);
    }
}
