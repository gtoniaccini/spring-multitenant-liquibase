package com.gtoniaccini.multitenant.tenant;

import java.util.List;

public interface TenantRegistryService {

    TenantMetadata requireActiveTenant(String tenantId);

    TenantMetadata findByNamespaceName(String namespaceName);

    List<TenantMetadata> listActiveTenants();
}