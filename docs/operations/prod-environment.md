# PROD environment (AIW-75)

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
   `/actuator/health` for real (it will still fail until AIW-76 wires real PROD database
   credentials in - the same DEV/STAGING sequencing precedent, not a regression).

## What is real and already isolated today

- **Key Vault** (`kv-aiw-prod-swc`) and **managed identity** (`id-aiw-backend-prod`) exist now,
  fully isolated from DEV/STAGING's own vault/identity - a DEV or STAGING credential has no
  access path to a PROD secret, and vice versa (same guarantee documented in
  `azure-environment-architecture.md`).
- **RBAC is least-privilege and already real**: `AcrPull` scoped to the shared registry only,
  `Key Vault Secrets Officer`/`Key Vault Secrets User` scoped to PROD's own vault only - no
  subscription- or resource-group-wide grant.
- **No DEV/STAGING dependency of any kind**: PROD's Key Vault holds no secrets yet (AIW-76 adds
  them from PROD's own, not-yet-provisioned, dedicated PostgreSQL server - never the shared
  NONPROD server DEV/STAGING use). This satisfies the AC "production does not depend on
  DEV/STAGING databases or secrets" by construction, not just by convention.
- **Frontend hosting** is fully live today, independent of the Container App blocker above.

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
