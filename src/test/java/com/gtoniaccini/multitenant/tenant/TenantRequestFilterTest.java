package com.gtoniaccini.multitenant.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class TenantRequestFilterTest {

    private final TenantProperties properties = new TenantProperties(
            "X-Tenant-ID", "public", "/api/", "db/changelog/db.changelog-tenant.yaml"
    );

    @Mock
    private TenantRegistryService registryService;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void doFilterInternal_setsTenantContextAndClearsAfterChain() throws Exception {
        TenantMetadata metadata = new TenantMetadata(
                "tenant_alpha", "tenant_alpha",
                "jdbc:postgresql://localhost:5432/tenant_alpha",
                "postgres", "postgres", "org.postgresql.Driver", true
        );
        when(registryService.requireActiveTenant("tenant_alpha")).thenReturn(metadata);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");
        request.addHeader("X-Tenant-ID", "tenant_alpha");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties);

        // capture the TenantContext state during chain execution
        AtomicCurrentTenant captured = new AtomicCurrentTenant();
        doAnswer(inv -> { captured.value = TenantContext.getCurrentTenant(); return null; })
                .when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(captured.value).isNotNull();
        assertThat(captured.value.tenantId()).isEqualTo("tenant_alpha");
        // after the filter the context must be cleared
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void doFilterInternal_throwsMissingTenantHeaderException_whenHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(MissingTenantHeaderException.class);

        verifyNoInteractions(registryService);
    }

    @Test
    void doFilterInternal_throwsMissingTenantHeaderException_whenHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");
        request.addHeader("X-Tenant-ID", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(MissingTenantHeaderException.class);
    }

    @Test
    void shouldNotFilter_returnsFalse_forProtectedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");

        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties);

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_returnsTrue_forPublicPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");

        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties);

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void resolveTenantId_canBeOverridden() throws Exception {
        TenantMetadata metadata = new TenantMetadata(
                "tenant_beta", "tenant_beta",
                "jdbc:postgresql://localhost:5432/tenant_beta",
                "postgres", "postgres", "org.postgresql.Driver", true
        );
        when(registryService.requireActiveTenant("tenant_beta")).thenReturn(metadata);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Subclass that reads from a custom header instead of X-Tenant-ID
        TenantRequestFilter filter = new TenantRequestFilter(registryService, properties) {
            @Override
            protected String resolveTenantId(HttpServletRequest req) {
                return req.getHeader("X-Custom-Tenant");
            }
        };
        request.addHeader("X-Custom-Tenant", "tenant_beta");

        filter.doFilter(request, response, filterChain);

        verify(registryService).requireActiveTenant("tenant_beta");
    }

    // small helper to capture value inside lambda
    private static class AtomicCurrentTenant {
        CurrentTenant value;
    }
}
