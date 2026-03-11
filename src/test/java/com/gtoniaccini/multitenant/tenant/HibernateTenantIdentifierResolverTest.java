package com.gtoniaccini.multitenant.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HibernateTenantIdentifierResolverTest {

    private final TenantProperties properties = new TenantProperties(
            "X-Tenant-ID", "public", "/api/", "db/changelog/db.changelog-tenant.yaml"
    );

    private final HibernateTenantIdentifierResolver resolver =
            new HibernateTenantIdentifierResolver(properties);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolveCurrentTenantIdentifier_returnsTenantNamespace_whenTenantIsSet() {
        TenantContext.setCurrentTenant(new CurrentTenant("tenant_alpha", "alpha_ns"));

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("alpha_ns");
    }

    @Test
    void resolveCurrentTenantIdentifier_returnsPublicSchema_whenNoTenantIsSet() {
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo("public");
    }

    @Test
    void validateExistingCurrentSessions_returnsTrue() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
