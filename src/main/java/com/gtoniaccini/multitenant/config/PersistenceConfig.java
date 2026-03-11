package com.gtoniaccini.multitenant.config;

import com.gtoniaccini.multitenant.tenant.DataSourceBasedMultiTenantConnectionProvider;
import com.gtoniaccini.multitenant.tenant.HibernateTenantIdentifierResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfig {

    @Bean
    HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            DataSourceBasedMultiTenantConnectionProvider connectionProvider,
            HibernateTenantIdentifierResolver tenantIdentifierResolver
    ) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}