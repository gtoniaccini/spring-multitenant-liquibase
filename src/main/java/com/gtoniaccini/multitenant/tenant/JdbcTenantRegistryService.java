package com.gtoniaccini.multitenant.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JdbcTenantRegistryService implements TenantRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantProperties tenantProperties;

    private final RowMapper<TenantMetadata> tenantRowMapper = (resultSet, rowNum) -> new TenantMetadata(
            resultSet.getString("tenant_id"),
            resultSet.getString("namespace_name"),
            resultSet.getString("url"),
            resultSet.getString("username"),
            resultSet.getString("password"),
            resultSet.getString("driver_class_name"),
            resultSet.getBoolean("active")
    );

    public JdbcTenantRegistryService(JdbcTemplate jdbcTemplate,
                                     TenantProperties tenantProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantProperties = tenantProperties;
    }

    private static final String SELECT_ALL = "select tenant_id, namespace_name, url, username, password, driver_class_name, active from ";

    @Override
    public TenantMetadata requireActiveTenant(String tenantId) {
        String sql = SELECT_ALL + tenantProperties.publicSchema() + ".tenants where tenant_id = ?";

        List<TenantMetadata> tenants = jdbcTemplate.query(sql, tenantRowMapper, tenantId);
        if (tenants.isEmpty() || !tenants.getFirst().active()) {
            throw new UnknownTenantException("Tenant not found or inactive: " + tenantId);
        }

        return tenants.getFirst();
    }

    @Override
    public TenantMetadata findByNamespaceName(String namespaceName) {
        String sql = SELECT_ALL + tenantProperties.publicSchema() + ".tenants where namespace_name = ? and active = true";

        List<TenantMetadata> tenants = jdbcTemplate.query(sql, tenantRowMapper, namespaceName);
        if (tenants.isEmpty()) {
            throw new UnknownTenantException("No active tenant with namespace: " + namespaceName);
        }

        return tenants.getFirst();
    }

    @Override
    public List<TenantMetadata> listActiveTenants() {
        String sql = SELECT_ALL + tenantProperties.publicSchema() + ".tenants where active = true order by tenant_id";
        return jdbcTemplate.query(sql, tenantRowMapper);
    }
}