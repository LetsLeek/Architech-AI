# DEV environment (AIW-71)

Real, currently-deployed URLs and access procedure for the platform's own DEV environment - not
the customer-website preview/production infrastructure, which is a different, not-yet-built
concern (see `azure-environment-architecture.md`'s own "Platform environments vs. customer
website environments" section).

## URLs

| Component | URL |
|---|---|
| Backend (Container App) | `https://ca-aiw-backend-dev.happyflower-cd7e5ebd.swedencentral.azurecontainerapps.io` |
| Frontend (Static Web App) | `https://lively-tree-0a6c93e10.5.azurestaticapps.net` |

Both are real Azure-assigned hostnames (Container Apps and Static Web Apps both generate a
random subdomain by default) - not yet mapped to a custom domain; that's a future decision, not
part of this ticket's scope.

## Health check gap: closed (AIW-73)

Real history, kept for context: the backend's Flyway migration step originally failed at startup
because `SPRING_DATASOURCE_URL` wasn't set (defaulted to `localhost:5432`, which doesn't exist
inside a Container App) - no real PostgreSQL server existed for DEV yet. This was an explicit,
user-confirmed decision to provision DEV's Container Apps/Static Web App infrastructure (AIW-71)
ahead of PostgreSQL (AIW-72) rather than block on it. **AIW-72** then stood up the real NONPROD
PostgreSQL server/`aiw_dev` database/`aiw_dev_app` role, narrowing the remaining gap to just the
Container App not yet reading those credentials. **AIW-73** closes it: the three
`SPRING_DATASOURCE_*` values are now resolved by the Container App at provision time from Key
Vault, via its own user-assigned managed identity (`Key Vault Secrets User` role, scoped to
`kv-aiw-dev-swc` only) - never a plain env var, never a value committed to git.

Real, current verification:

```
$ curl https://ca-aiw-backend-dev.happyflower-cd7e5ebd.swedencentral.azurecontainerapps.io/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
```

### Secret naming and rotation

Each Key Vault secret name matches the Spring Boot property it backs, kebab-cased
(`spring-datasource-url` → `SPRING_DATASOURCE_URL` via the Container App's `secret_env` mapping in
`infrastructure/modules/container-app/variables.tf`) - a new secret follows the same
`<spring-property-kebab-case>` convention. All three DEV secrets carry a 1-year
`expiration_date` (Azure surfaces an expiring-soon warning in the portal/`az keyvault secret
list`; nothing yet auto-rotates on expiry - a manual `terraform apply` after generating a new
`random_password` in `environments/nonprod` is today's real rotation procedure). No automated
rotation pipeline exists yet - acceptable for a non-prod credential set at this project's current
size, revisited if/when PROD (AIW-76) needs a stricter answer.

### Future AI provider keys

The same pattern generalizes without new infrastructure: any future secret (an Anthropic/OpenAI
API key, say) becomes one more `azurerm_key_vault_secret` in this file plus one more
`key_vault_secrets`/`secret_env` entry on the `backend` module block - the vault, the managed
identity, and the RBAC role assignment set up by this ticket are already the real, working access
path. No per-secret infrastructure change is needed beyond that.

## Deployment procedure

**Backend image**: pushed by `.github/workflows/backend-ci.yml`'s `docker-build` job on every
push to `develop` (AIW-70), tagged with that commit's full Git SHA. Deploying a new image to DEV
is a separate, explicit step - there is no auto-redeploy-on-push wiring yet (a reasonable next
increment, not built here to keep this ticket's own scope to provisioning, not continuous
deployment):

```bash
cd infrastructure/environments/dev
terraform init -backend-config=... # see infrastructure/README.md
terraform plan -var="backend_image_tag=<the new commit SHA>"
terraform apply -var="backend_image_tag=<the new commit SHA>"
```

**Frontend**: `.github/workflows/frontend-deploy-dev.yml` deploys automatically on every push to
`develop` that touches `frontend/**` - builds with `VITE_API_BASE_URL` set to the real backend
URL above, then uploads to the Static Web App via Azure's own deployment token
(`AZURE_STATIC_WEB_APPS_API_TOKEN_DEV`, a real GitHub Actions secret - Azure's
`static-web-apps-deploy` action authenticates with this token, not OIDC, since that's the
action's own supported auth model).

## Developer access

Anyone with `Reader` (or higher) role on `rg-aiw-dev-swc` can view every resource in the Azure
portal or via `az`; `Contributor` (or the specific `Storage Blob Data Contributor` /
`Microsoft.App/*` write permissions) is needed to actually run `terraform apply` against this
environment. Role assignment is manual today (via `az role assignment create` or the portal) -
no automated onboarding process exists yet, appropriate for a team of this current size.

```bash
az login
az containerapp logs show --name ca-aiw-backend-dev --resource-group rg-aiw-dev-swc --follow
az containerapp revision list --name ca-aiw-backend-dev --resource-group rg-aiw-dev-swc -o table
```

## CORS

The backend allows cross-origin requests from the real frontend origin above only
(`ARCHITECH_CORS_ALLOWEDORIGINS` env var → `architech.cors.allowed-origins`, see
`backend/src/main/java/ai/architech/backend/core/web/`) - empty by default, so local development
(same-origin via Vite's own dev-server proxy) is unaffected by this environment-specific config.
