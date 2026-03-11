# Spring Multitenant Liquibase

[![CI](https://github.com/gtoniaccini/spring-multitenant-liquibase/actions/workflows/ci.yml/badge.svg)](https://github.com/gtoniaccini/spring-multitenant-liquibase/actions/workflows/ci.yml)

This project demonstrates **database-per-tenant** multitenancy in Spring Boot using PostgreSQL, a master tenant registry and Liquibase migrations applied to each tenant database on startup.

The project is intentionally split into two parts:

- a reusable multitenancy core that can be copied into another service
- a small Customer domain used only to prove that tenant isolation works end to end

## What the demo shows

- A master database (`multitenant_master`) holds a `public.tenants` registry with the connection details for every tenant.
- Each tenant has its own dedicated PostgreSQL database (`tenant_alpha`, `tenant_beta`), created via a Docker init script.
- Hibernate is configured with the `DATABASE` multitenancy strategy: every tenant gets its own `HikariDataSource`, lazy-loaded on first use.
- The tenant is resolved from the HTTP header `X-Tenant-ID` by default. The resolution logic is easily overridable (see below).
- `SpringLiquibase` runs each tenant changelog on startup via `ApplicationRunner`, after the master schema is already in place.
- A simple Customer CRUD API proves that tenant data stays isolated.

## Tech stack

- Java 21
- Spring Boot 3.4
- Spring Web
- Spring Data JPA
- Hibernate 6
- Liquibase
- PostgreSQL

## Project scope

- Master database: `multitenant_master` with table `public.tenants`
- Two demo tenants: `tenant_alpha` and `tenant_beta`
- One business entity: `Customer`
- One tenant-aware REST API under `/api/customers`

## Reusable multitenancy core

The reusable part lives in the `tenant` and `liquibase` packages.

| Class | Responsibility |
|---|---|
| `TenantProperties` | `@ConfigurationProperties` record binding all `app.tenant.*` settings |
| `TenantMetadata` | Immutable value object representing a tenant (id, namespace, connection details) |
| `CurrentTenant` | Per-request record holding tenant id and namespace name |
| `TenantContext` | `ThreadLocal` holder for the current `CurrentTenant` |
| `TenantRegistryService` | Interface for tenant registry access |
| `JdbcTenantRegistryService` | JDBC implementation — avoids JPA circular dependency with Hibernate multitenancy |
| `TenantRequestFilter` | `OncePerRequestFilter` that resolves the tenant and populates `TenantContext` |
| `HibernateTenantIdentifierResolver` | Hibernate SPI: returns the current tenant namespace for query routing |
| `DataSourceBasedMultiTenantConnectionProvider` | Hibernate SPI: manages a lazy `ConcurrentHashMap` of per-tenant `HikariDataSource` instances |
| `TenantLiquibaseMigrator` | `ApplicationRunner` that applies `SpringLiquibase` to every active tenant on startup |

The `customer` package is demo code only. In a real project replace it with your own domain and leave the multitenancy core untouched.

## Customising tenant resolution

By default, `TenantRequestFilter` reads the tenant identifier from the `X-Tenant-ID` HTTP header. To change this behaviour (for example to read a claim from a JWT), extend the filter and override `resolveTenantId`:

```java
@Component
public class JwtTenantRequestFilter extends TenantRequestFilter {

    public JwtTenantRequestFilter(TenantRegistryService registry, TenantProperties props) {
        super(registry, props);
    }

    @Override
    protected String resolveTenantId(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return JwtUtils.extractClaim(bearer, "tenant_id");
    }
}
```

Everything else — registry lookup, `TenantContext` population and cleanup — works unchanged.

## How to reuse the core in another project

1. Copy the `tenant` and `liquibase` packages into your service.
2. Add the `app.tenant.*` properties to your `application.yml` (see configuration below).
3. Keep a `public.tenants` table in your master database with the columns: `tenant_id`, `namespace_name`, `url`, `username`, `password`, `driver_class_name`, `active`.
4. Replace the `customer` package with your own domain.
5. Point `tenant-changelog` to your own per-tenant Liquibase changelog.
6. Adjust `protected-path-prefix` to match your API base path.

## Configuration reference

```yaml
app:
  tenant:
    header-name: X-Tenant-ID           # HTTP header used to identify the tenant
    public-schema: public              # Schema used for the master registry
    protected-path-prefix: /api/       # Requests under this prefix require a tenant header
    tenant-changelog: db/changelog/db.changelog-tenant.yaml  # Per-tenant Liquibase changelog
```

## Run locally

The Docker Compose file starts a single PostgreSQL instance. The `docker/init-databases.sql` script creates the tenant databases automatically on first run.

```bash
docker compose up -d
mvn spring-boot:run
```

Wait a few seconds for PostgreSQL to be ready before starting the application.

## Example requests

Create a customer for `tenant_alpha`:

```bash
curl --request POST http://localhost:8080/api/customers \
  --header "Content-Type: application/json" \
  --header "X-Tenant-ID: tenant_alpha" \
  --data '{"name":"Alice","email":"alice@example.com"}'
```

List customers for `tenant_beta`:

```bash
curl --request GET http://localhost:8080/api/customers \
  --header "X-Tenant-ID: tenant_beta"
```

## Architecture notes

- **No circular dependency**: the tenant registry uses `JdbcTemplate` instead of JPA, so Hibernate multitenancy configuration does not depend on an already-initialised `EntityManagerFactory`.
- **Single `EntityManagerFactory`**: Spring Boot auto-configures one EMF; `PersistenceConfig` only adds the two Hibernate multitenancy properties via `HibernatePropertiesCustomizer`.
- **Lazy DataSource loading**: tenant `DataSource` instances are created on first request, not at startup, so the tenants table does not need to exist when the Spring context is built.
- **Pool cleanup in migrations**: `TenantLiquibaseMigrator` closes each temporary `HikariDataSource` after the migration runs to avoid connection pool leaks.

## Testing

The project has two test layers:

### Unit tests (`mvn test`)

Focused on the multitenancy core in isolation, no Spring context loaded.

| Test class | What it covers |
|---|---|
| `TenantContextTest` | `ThreadLocal` isolation — set, get, clear, missing tenant |
| `HibernateTenantIdentifierResolverTest` | Correct namespace returned, exception when context is empty |
| `TenantRequestFilterTest` | Missing header → `MissingTenantHeaderException`, unknown tenant → `UnknownTenantException`, happy path populates context |

### Integration tests (`mvn verify`, requires Docker)

`MultitenancyIT` spins up a real PostgreSQL 16 instance via **Testcontainers** and verifies end-to-end behaviour against a live database.

| Test | What it verifies |
|---|---|
| `customers_areIsolatedBetweenTenants` | A customer created for `tenant_alpha` is visible in `tenant_alpha` and invisible in `tenant_beta` |
| `missingTenantHeader_throwsMissingTenantHeaderException` | A request with no `X-Tenant-ID` header raises the correct exception |
| `unknownTenant_throwsUnknownTenantException` | A request with an unregistered tenant id raises the correct exception |

The container hosts three databases (`multitenant_master`, `tenant_alpha`, `tenant_beta`) in a single PostgreSQL instance. `TenantLiquibaseMigrator` is replaced with a mock at startup so tenant URLs can be rewritten to the dynamic Testcontainers port before migrations run.