package com.gtoniaccini.multitenant.tenant;

public record TenantMetadata(
        String tenantId,
        String namespaceName,
        String url,
        String username,
        String password,
        String driverClassName,
        boolean active
) {
}