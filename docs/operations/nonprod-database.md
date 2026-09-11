# NONPROD PostgreSQL (AIW-72)

Real, currently-provisioned shared PostgreSQL Flexible Server for DEV and STAGING - PROD gets
its own separate, dedicated server (AIW-76), never this one.

## Server

| Property | Value |
|---|---|
| Name | `psql-aiw-nonprod-swc` |
| Resource group | `rg-aiw-nonprod-swc` |
| FQDN | `psql-aiw-nonprod-swc.postgres.database.azure.com` |
| SKU | `B_Standard_B1ms` (Burstable) |
| Storage | 32 GiB |
| Version | PostgreSQL 16 |
| Backup retention | 7 days |

**Cost-conscious by deliberate choice, not oversight**: Burstable B1ms is the smallest real
Flexible Server tier - matches this project's own established "start at the honest baseline"
posture (the same one applied to coverage thresholds and ACR's Basic SKU). Revisit once real
DEV/STAGING usage patterns are known, not pre-emptively.

## Databases and roles

Two databases, two separate least-privilege roles - **not** a shared admin credential handed to
either application:

| Database | Owning role | Used by |
|---|---|---|
| `aiw_dev` | `aiw_dev_app` | DEV backend |
| `aiw_staging` | `aiw_staging_app` | STAGING backend |

Real isolation, not assumed - verified with actual `psql` connections while building this
ticket, catching two genuine gaps neither database ownership nor "owns its own database" alone
actually closes:

1. **PostgreSQL grants `CONNECT` to every database to the `PUBLIC` pseudo-role by default,
   regardless of ownership.** A real connection as `aiw_dev_app` to `aiw_staging` succeeded
   before `postgresql_grant` resources explicitly revoking `PUBLIC`'s privileges on each
   database existed. After: `FATAL: permission denied for database "aiw_staging"` (or
   `aiw_dev`, symmetric both directions - both directions were actually tested).
2. **PostgreSQL 15+ no longer gives a database's nominal owner implicit `CREATE` on that
   database's own `public` schema** (the schema has its own separate owner/ACL). A real
   `CREATE TABLE` as `aiw_dev_app` on `aiw_dev` failed with `permission denied for schema
   public` before an explicit schema-level grant existed - which would have broken Flyway's very
   first migration (creating its own `schema_history` table) identically. Fixed, then re-verified
   with a real `CREATE TABLE`/`DROP TABLE` against both databases as both app roles.

This is why AIW-72's own acceptance criterion ("Flyway migrations can be applied independently to
each database") is asserted here with real evidence, not just structural reasoning about
ownership.

## Connectivity

Public network access, firewall-gated (no private networking/VNet yet - that's AIW-76's own
concern for PROD, not part of NONPROD's scope):

- `azure-services` (`0.0.0.0`/`0.0.0.0`, Azure's own documented special case) - lets Azure
  Container Apps actually reach this server once AIW-73 wires the connection in.
- `terraform-admin` - the current maintainer's own public IP, needed only because the
  `postgresql` Terraform provider connects for real (not through the Azure control plane) to
  create the databases/roles above. Revisit if this project ever needs `apply` to run from CI
  instead of a human's own machine - a real, deliberately narrow allowlist, not a placeholder to
  widen carelessly later.

## What this ticket does *not* do

AIW-72 provisions the server, databases, and roles - it does **not** connect the DEV backend to
any of it. The real DEV backend Container App (AIW-71) still doesn't have
`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` set - that wiring is AIW-73's own scope (Key
Vault + the Container App's secret references), since these are real credentials that must not
land in a Container App's plain (non-secret) environment variables. **The DEV health-check gap
documented in `dev-environment.md` is not closed by this ticket** - it will be, once AIW-73
lands.
