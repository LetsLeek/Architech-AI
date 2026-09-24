# PROD environment (AIW-75, AIW-76)

Real, currently-deployed URLs and access procedure for the platform's own PRODUCTION
environment - mirrors `dev-environment.md`/`staging-environment.md`'s own structure.

## URLs

| Component | URL | Status |
|---|---|---|
| Backend (Container App) | not yet created | **Blocked** - see below |
| Frontend (Static Web App) | `https://white-ground-03176f910.6.azurestaticapps.net` | Live |

## Open blocker: Container Apps Environment quota

This subscription has a real, confirmed hard quota of exactly **1 Container App Environment
total** (`MaxNumberOfGlobalEnvironmentsInSubExceeded` - subscription-wide, not per-region; a
first attempt at a second, region-distinct environment for STAGING (AIW-74) hit the identical
error). DEV and STAGING already share that one environment (AIW-74's own documented exception),
but reusing it for PROD would:

- violate this ticket's own explicit AC ("stronger isolation and access controls than
  non-production" / "isolated production Container App configuration"), and
- violate the "never extended to PROD" boundary already established for every other
  shared-non-prod exception in this project (NONPROD PostgreSQL, the shared Container Apps
  Environment itself).

So this ticket deliberately does **not** create PROD's Container Apps Environment or Container
App yet. Both are fully written and reviewed in `infrastructure/environments/prod/main.tf`
(`module.container_app_environment`, `module.backend`) - real, promotion-ready configuration,
left un-applied (pinned) pending a real Azure quota increase, not a placeholder guess. `terraform
plan` against this environment will keep showing these 2 resources as pending until the increase
is granted and `terraform apply` is run for them specifically - that pending diff is expected,
not a sign of drift or a broken apply.

### How to request the quota increase (manual, requires an Azure Portal action)

1. Azure Portal → **Quotas** → **App Service/Container Apps** (or search "Container Apps
   quotas") → select this subscription.
2. Find **Container App Environments per subscription** (or the equivalent
   `Microsoft.App`/managed-environment quota row).
3. Request an increase (2 is enough for now: PROD + the existing shared DEV/STAGING one).
4. Once approved (Microsoft support/quota review, not instant), run from
   `infrastructure/environments/prod`:
   ```bash
   export ARM_USE_MSI=false
   terraform plan -var="backend_image_tag=<the SHA already deployed to STAGING>" -out=prod.tfplan
   terraform apply "prod.tfplan"
   ```
5. Update this file's URLs table with the real backend FQDN once created, and verify
   `/actuator/health` for real. AIW-76's real `aiw_prod` database/credentials already exist and
   are already wired into the Container App's own (currently un-applied) configuration - once
   unblocked, this should just work on the first try, unlike DEV's original AIW-71→AIW-73 gap.

## What is real and already isolated today

- **Key Vault** (`kv-aiw-prod-swc`) and **managed identity** (`id-aiw-backend-prod`) exist now,
  fully isolated from DEV/STAGING's own vault/identity - a DEV or STAGING credential has no
  access path to a PROD secret, and vice versa (same guarantee documented in
  `azure-environment-architecture.md`).
- **RBAC is least-privilege and already real**: `AcrPull` scoped to the shared registry only,
  `Key Vault Secrets Officer`/`Key Vault Secrets User` scoped to PROD's own vault only - no
  subscription- or resource-group-wide grant.
- **No DEV/STAGING dependency of any kind**: PROD's Key Vault holds the real `aiw_prod`
  database's own credentials (AIW-76), sourced from PROD's own dedicated PostgreSQL server -
  never the shared NONPROD server DEV/STAGING use, and never read via `terraform_remote_state`
  from another environment's state (unlike STAGING's Container Apps Environment reuse). This
  satisfies the AC "production does not depend on DEV/STAGING databases or secrets" by
  construction, not just by convention.
- **Frontend hosting** is fully live today, independent of the Container App blocker above.

## Database (AIW-76)

`psql-aiw-prod-swc` - PROD's own dedicated PostgreSQL Flexible Server, provisioned separately
from `psql-aiw-nonprod-swc` (never shared, per the "never extended to PROD" exception documented
in `azure-environment-architecture.md`). Real, verified today:

- **Database/credentials**: `aiw_prod` database, owned by a dedicated `aiw_prod_app` role (not a
  superuser, no access to any other database) - verified via a real `psql` connection that the
  role can connect and `CREATE TABLE` on its own database (the same two real PG15+/PUBLIC-grant
  findings from AIW-72 applied here from the start, not re-discovered).
- **Backups**: automatic, 35-day retention (Azure's real maximum, vs. NONPROD's 7-day default) -
  point-in-time recovery works within that window with no separate toggle beyond retention days.
  `geo_redundant_backup_enabled = true` (NONPROD stays `false`) for real cross-region DR
  protection appropriate for production data.
- **Restore procedure**: Azure Portal → the server resource → **Restore** → pick a target
  timestamp within the last 35 days → restores to a **new** server (Flexible Server's real
  behavior - it does not restore in place). Point the Key Vault's `spring-datasource-url` secret
  at the restored server's new FQDN and re-apply, or `az postgres flexible-server restore` for
  the CLI equivalent. This is a rare, deliberate, human-initiated operation - never automated.
- **Network**: not exposed broadly to the public internet - deny-all firewall plus exactly two
  allowed entries (`azure-services`, the documented 0.0.0.0/0.0.0.0 special case for the eventual
  PROD Container App; `terraform-admin`, this project's own real admin IP). **Honest trade-off,
  not the ideal end state**: a true Private Endpoint isn't practical yet because this platform's
  Container Apps Environment has no VNet integration at all (a genuinely bigger change, and one
  still blocked today by the same Container Apps Environment quota limit above) - revisit once
  VNet integration exists for the runtime.
- **High Availability**: **not enabled**, deliberately - this is a documented later scaling
  option (AC), not something to turn on without a real need. Azure Postgres Flexible Server
  supports zone-redundant or same-zone HA as an in-place upgrade (`high_availability` block on
  `azurerm_postgresql_flexible_server`) whenever real production traffic/uptime requirements
  justify the added cost - revisit then, not preemptively.

## Deployment target (once unblocked)

Same shape as DEV/STAGING - promote the identical image artifact, tagged with the Git SHA
already verified in STAGING:

```bash
cd infrastructure/environments/prod
terraform init -backend-config=... # see infrastructure/README.md
terraform plan -var="backend_image_tag=<the SHA already deployed to STAGING>"
terraform apply -var="backend_image_tag=<the SHA already deployed to STAGING>"
```

No automated PROD deploy-on-push workflow exists (by design - a deliberate, explicit
promotion step, not an accidental gap), consistent with DEV/STAGING today.
