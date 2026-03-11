package com.gtoniaccini.multitenant.liquibase;

import com.gtoniaccini.multitenant.tenant.DataSourceBasedMultiTenantConnectionProvider;
import com.gtoniaccini.multitenant.tenant.TenantMetadata;
import com.gtoniaccini.multitenant.tenant.TenantProperties;
import com.gtoniaccini.multitenant.tenant.TenantRegistryService;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class TenantLiquibaseMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantLiquibaseMigrator.class);

    private final TenantRegistryService tenantRegistryService;
    private final TenantProperties tenantProperties;
    private final ResourceLoader resourceLoader;

    public TenantLiquibaseMigrator(TenantRegistryService tenantRegistryService,
                                   TenantProperties tenantProperties,
                                   ResourceLoader resourceLoader) {
        this.tenantRegistryService = tenantRegistryService;
        this.tenantProperties = tenantProperties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TenantMetadata> tenants = tenantRegistryService.listActiveTenants();
        tenants.forEach(this::applyTenantChangelog);
    }

    private void applyTenantChangelog(TenantMetadata tenantMetadata) {
        log.info("Applying Liquibase changelog for tenant: {}", tenantMetadata.tenantId());
        DataSource tenantDataSource = DataSourceBasedMultiTenantConnectionProvider.buildTenantDataSource(tenantMetadata);
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setResourceLoader(resourceLoader);
            liquibase.setDataSource(tenantDataSource);
            liquibase.setChangeLog(tenantProperties.tenantChangelog());
            liquibase.afterPropertiesSet();
            log.info("Liquibase ran successfully for tenant: {}", tenantMetadata.tenantId());
        } catch (LiquibaseException exception) {
            throw new IllegalStateException(
                    "Failed to apply tenant migrations for " + tenantMetadata.tenantId(),
                    exception
            );
        } finally {
            if (tenantDataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
    }
}