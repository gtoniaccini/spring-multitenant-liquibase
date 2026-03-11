package com.gtoniaccini.multitenant.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(
        String headerName,
        String publicSchema,
        String protectedPathPrefix,
        String tenantChangelog
) {
}