package com.gtoniaccini.multitenant.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantRequestFilter extends OncePerRequestFilter {

    private final TenantRegistryService tenantRegistryService;
    private final TenantProperties tenantProperties;

    public TenantRequestFilter(TenantRegistryService tenantRegistryService,
                               TenantProperties tenantProperties) {
        this.tenantRegistryService = tenantRegistryService;
        this.tenantProperties = tenantProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(tenantProperties.protectedPathPrefix());
    }

    /**
     * Resolves the tenant identifier from the incoming request.
     * <p>
     * Override this method to customize how the tenant is determined.
     * Common alternatives include reading a claim from a JWT token,
     * extracting a value from an OAuth2 principal, or reading a cookie.
     * <p>
     * Return {@code null} or a blank string to trigger a missing-tenant error.
     */
    protected String resolveTenantId(HttpServletRequest request) {
        return request.getHeader(tenantProperties.headerName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = resolveTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantHeaderException("Tenant identifier is required");
        }

        TenantMetadata tenantMetadata = tenantRegistryService.requireActiveTenant(tenantId);

        try {
            TenantContext.setCurrentTenant(new CurrentTenant(tenantMetadata.tenantId(), tenantMetadata.namespaceName()));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}