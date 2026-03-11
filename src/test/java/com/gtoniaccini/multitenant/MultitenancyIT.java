package com.gtoniaccini.multitenant;

import com.gtoniaccini.multitenant.liquibase.TenantLiquibaseMigrator;
import com.gtoniaccini.multitenant.tenant.DataSourceBasedMultiTenantConnectionProvider;
import com.gtoniaccini.multitenant.tenant.MissingTenantHeaderException;
import com.gtoniaccini.multitenant.tenant.TenantMetadata;
import com.gtoniaccini.multitenant.tenant.UnknownTenantException;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests that verify database-per-tenant isolation using real PostgreSQL
 * containers. Three databases run inside a single container:
 * <ul>
 *   <li>{@code multitenant_master} – tenant registry</li>
 *   <li>{@code tenant_alpha} and {@code tenant_beta} – tenant databases</li>
 * </ul>
 * <p>
 * {@link TenantLiquibaseMigrator} is disabled during context startup ({@code @MockBean})
 * so that tenant URLs can be updated to the dynamic Testcontainers port before the
 * migration runs. {@link #setUpTenants()} then fixes the URLs and runs Liquibase
 * programmatically for each tenant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultitenancyIT {

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("multitenant_master")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("testcontainers/init-tenant-databases.sql");

    // Start the container in a static initializer so the port is available
    // before @DynamicPropertySource is evaluated by Spring's extension.
    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Disabled at startup so we can fix tenant URLs before migration runs. */
    @MockitoBean
    TenantLiquibaseMigrator tenantLiquibaseMigrator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ResourceLoader resourceLoader;

    @BeforeAll
    void setUpTenants() throws Exception {
        String baseUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432);

        // Replace the hardcoded localhost:5432 URLs seeded by the public Liquibase changelog
        jdbcTemplate.update("UPDATE public.tenants SET url = ? WHERE tenant_id = ?",
                baseUrl + "/tenant_alpha", "tenant_alpha");
        jdbcTemplate.update("UPDATE public.tenants SET url = ? WHERE tenant_id = ?",
                baseUrl + "/tenant_beta", "tenant_beta");

        // Apply tenant schema (Liquibase changelog) to each tenant database
        runTenantMigration(baseUrl + "/tenant_alpha");
        runTenantMigration(baseUrl + "/tenant_beta");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void customers_areIsolatedBetweenTenants() throws Exception {
        // Create a customer in tenant_alpha
        mockMvc.perform(post("/api/customers")
                        .header("X-Tenant-ID", "tenant_alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Alice\", \"email\": \"alice@alpha-isolation.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"));

        // tenant_alpha can see Alice
        mockMvc.perform(get("/api/customers")
                        .header("X-Tenant-ID", "tenant_alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem("alice@alpha-isolation.com")));

        // tenant_beta cannot see Alice
        mockMvc.perform(get("/api/customers")
                        .header("X-Tenant-ID", "tenant_beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", not(hasItem("alice@alpha-isolation.com"))));
    }

    /**
     * Exceptions thrown by servlet filters are not handled by @RestControllerAdvice
     * (which only intercepts exceptions from the DispatcherServlet layer).
     * Here we verify the correct exception type is raised instead of an HTTP status.
     */
    @Test
    void missingTenantHeader_throwsMissingTenantHeaderException() {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/customers")))
                .isInstanceOf(MissingTenantHeaderException.class)
                .hasMessage("Tenant identifier is required");
    }

    @Test
    void unknownTenant_throwsUnknownTenantException() {
        assertThatThrownBy(() -> mockMvc.perform(get("/api/customers")
                        .header("X-Tenant-ID", "tenant_unknown")))
                .isInstanceOf(UnknownTenantException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void runTenantMigration(String url) throws Exception {
        TenantMetadata metadata = new TenantMetadata(
                "", "", url,
                postgres.getUsername(), postgres.getPassword(),
                "org.postgresql.Driver", true
        );
        HikariDataSource ds = (HikariDataSource) DataSourceBasedMultiTenantConnectionProvider.buildTenantDataSource(metadata);
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setResourceLoader(resourceLoader);
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-tenant.yaml");
            liquibase.setDataSource(ds);
            liquibase.afterPropertiesSet();
        } finally {
            ds.close();
        }
    }
}
