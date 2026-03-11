package com.gtoniaccini.multitenant.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGetCurrentTenant() {
        CurrentTenant tenant = new CurrentTenant("tenant_alpha", "tenant_alpha");
        TenantContext.setCurrentTenant(tenant);

        assertThat(TenantContext.getCurrentTenant()).isEqualTo(tenant);
    }

    @Test
    void getCurrentTenantNamespace_returnsTenantNamespace() {
        TenantContext.setCurrentTenant(new CurrentTenant("tenant_alpha", "alpha_ns"));

        assertThat(TenantContext.getCurrentTenantNamespace()).isEqualTo("alpha_ns");
    }

    @Test
    void getCurrentTenantNamespace_returnsNullWhenNoTenantSet() {
        assertThat(TenantContext.getCurrentTenantNamespace()).isNull();
    }

    @Test
    void clear_removesCurrentTenant() {
        TenantContext.setCurrentTenant(new CurrentTenant("tenant_alpha", "tenant_alpha"));
        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void tenantContext_isIsolatedBetweenThreads() throws InterruptedException {
        TenantContext.setCurrentTenant(new CurrentTenant("main_tenant", "main_tenant"));

        AtomicReference<CurrentTenant> tenantInOtherThread = new AtomicReference<>();
        Thread other = new Thread(() -> tenantInOtherThread.set(TenantContext.getCurrentTenant()));
        other.start();
        other.join();

        assertThat(tenantInOtherThread.get())
                .as("ThreadLocal must not leak to other threads")
                .isNull();
    }
}
