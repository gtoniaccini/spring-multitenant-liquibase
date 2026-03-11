package com.gtoniaccini.multitenant.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements DATABASE-per-tenant multitenancy.
 * Each tenant has its own dedicated DataSource built lazily from the tenant registry.
 *
 * Reuse: copy this class along with the rest of the tenant package.
 * The only configuration required is the app.tenant properties.
 */
@Component
public class DataSourceBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    private final TenantRegistryService tenantRegistryService;
    private final DataSource masterDataSource;
    private final TenantProperties tenantProperties;

    public DataSourceBasedMultiTenantConnectionProvider(TenantRegistryService tenantRegistryService,
                                                        DataSource masterDataSource,
                                                        TenantProperties tenantProperties) {
        this.tenantRegistryService = tenantRegistryService;
        this.masterDataSource = masterDataSource;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return masterDataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (tenantIdentifier == null || tenantIdentifier.equals(tenantProperties.publicSchema())) {
            return masterDataSource.getConnection();
        }
        return resolveDataSource(tenantIdentifier).getConnection();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isAssignableFrom(getClass());
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Unsupported unwrap type: " + unwrapType.getName());
    }

    private DataSource resolveDataSource(String namespaceName) {
        return tenantDataSources.computeIfAbsent(namespaceName, key -> {
            TenantMetadata metadata = tenantRegistryService.findByNamespaceName(key);
            return buildTenantDataSource(metadata);
        });
    }

    /**
     * Builds a Hikari connection pool for the given tenant.
     * Package-visible so TenantLiquibaseMigrator can reuse it.
     */
    public static DataSource buildTenantDataSource(TenantMetadata tenantMetadata) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(tenantMetadata.url());
        ds.setUsername(tenantMetadata.username());
        ds.setPassword(tenantMetadata.password());
        if (tenantMetadata.driverClassName() != null && !tenantMetadata.driverClassName().isBlank()) {
            ds.setDriverClassName(tenantMetadata.driverClassName());
        }
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setPoolName(tenantMetadata.tenantId() + "-connection-pool");
        return ds;
    }
}