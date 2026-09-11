# STAGING environment (AIW-74)

Real, currently-deployed URLs and access procedure for the platform's own STAGING environment -
mirrors `dev-environment.md`'s own structure; see that file for the parts (deployment procedure
shape, developer access, CORS) that are identical in kind and not repeated in full here.

## URLs

| Component | URL |
|---|---|
| Backend (Container App) | `https://ca-aiw-backend-staging.happyflower-cd7e5ebd.swedencentral.azurecontainerapps.io` |
| Frontend (Static Web App) | `https://ashy-coast-098c93b10.6.azurestaticapps.net` |

Both are real Azure-assigned hostnames, not yet mapped to a custom domain.

## Health check

Real, current verification:

```
$ curl https://ca-aiw-backend-staging.happyflower-cd7e5ebd.swedencentral.azurecontainerapps.io/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
```

Backed by the real `aiw_staging` database/`aiw_staging_app` role (AIW-72) via the same
Key-Vault-backed secret pattern as DEV (AIW-73) - `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`
resolved at Container App provision time from STAGING's own Key Vault (`kv-aiw-staging-swc`),
never a plain env var.

## Real deviation from the original one-Container-Apps-Environment-per-tier plan

This subscription allows only **one** Container App Environment total (a real
`MaxNumberOfGlobalEnvironmentsInSubExceeded` error, not a per-region limit as initially assumed -
a first attempt at a second, region-distinct environment in Central US hit the same error).
STAGING's Container App therefore runs inside DEV's own Container Apps Environment
(`cae-aiw-dev-swc`), read via `terraform_remote_state` on `dev.tfstate` - see
`azure-environment-architecture.md`'s "Shared vs. environment-specific resources" section for the
full reasoning and the explicit "never extended to PROD" boundary. STAGING's Container App
remains a fully separate resource (own FQDN, ingress, identity, secrets) - only the
networking/logging boundary is shared, not traffic.

STAGING keeps its own Key Vault (`kv-aiw-staging-swc`) and its own user-assigned managed
identity (`id-aiw-backend-staging`), isolated from DEV's - a DEV credential has no access path to
a STAGING secret, matching the isolation guarantee already documented for DEV/PROD.

## Deployment procedure

Same shape as DEV (`dev-environment.md`'s own section) - promote the identical image artifact,
tagged with the Git SHA to deploy:

```bash
cd infrastructure/environments/staging
terraform init -backend-config=... # see infrastructure/README.md
terraform plan -var="backend_image_tag=<the new commit SHA>"
terraform apply -var="backend_image_tag=<the new commit SHA>"
```

No automated STAGING deploy-on-push workflow exists yet (same as DEV) - deploying a new image or
frontend build to STAGING is a deliberate, explicit step, appropriate ahead of AIW-79+'s own
release-pipeline work.
