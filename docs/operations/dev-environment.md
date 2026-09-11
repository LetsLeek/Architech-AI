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

## Known gap: backend health checks do not currently pass

Real, verified log output from the deployed Container App
(`az containerapp logs show --name ca-aiw-backend-dev --resource-group rg-aiw-dev-swc`):

```
F Message    : Connection to localhost:5432 refused. Check that the hostname and port are
correct and that the postmaster is accepting TCP/IP connections.
F Caused by: org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
```

The backend's Flyway migration step fails at startup because `SPRING_DATASOURCE_URL` isn't set
(defaults to `localhost:5432`, which doesn't exist inside a Container App) - no real PostgreSQL
server has been provisioned for DEV yet (AIW-72, not yet done). The container stays in
`Activating`/no health state indefinitely (`az containerapp revision list` confirms this) rather
than serving traffic.

**This is expected, not silently accepted as fine**: this ticket's own acceptance criterion
("health checks succeed after deployment") is not fully met today, by explicit decision
(confirmed with the user before implementing) rather than by omission - provisioning DEV's
Container Apps/Static Web App infrastructure now, ahead of AIW-72's PostgreSQL server, was judged
more valuable than blocking this ticket on that one.

**Update (AIW-72, done)**: the real NONPROD PostgreSQL server, `aiw_dev` database, and
least-privilege `aiw_dev_app` role now exist - see `nonprod-database.md` for the real
connectivity/isolation verification. This gap is **still open**, though, for a narrower reason
now: `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` still aren't set on the DEV Container App -
that wiring is AIW-73's own scope (Key Vault + the Container App's secret references, per
`secret-management.md`'s decision), deliberately not done directly as plain env vars even though
the real values now exist, since these are real credentials that must not land in a Container
App's non-secret configuration. AIW-73 is what actually closes this gap.

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
