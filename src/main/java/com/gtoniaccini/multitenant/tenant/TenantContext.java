package com.gtoniaccini.multitenant.tenant;

public final class TenantContext {

    private static final ThreadLocal<CurrentTenant> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(CurrentTenant currentTenant) {
        CURRENT_TENANT.set(currentTenant);
    }

    public static CurrentTenant getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static String getCurrentTenantNamespace() {
        CurrentTenant currentTenant = CURRENT_TENANT.get();
        return currentTenant != null ? currentTenant.namespaceName() : null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}