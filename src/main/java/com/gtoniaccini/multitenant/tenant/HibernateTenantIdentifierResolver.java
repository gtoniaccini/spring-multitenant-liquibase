package com.gtoniaccini.multitenant.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class HibernateTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenantProperties tenantProperties;

    public HibernateTenantIdentifierResolver(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String currentTenantNamespace = TenantContext.getCurrentTenantNamespace();
        return currentTenantNamespace != null ? currentTenantNamespace : tenantProperties.publicSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}