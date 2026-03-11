package com.gtoniaccini.multitenant.tenant;

public record CurrentTenant(
        String tenantId,
        String namespaceName
) {
}