# STAGING deployment pipeline (AIW-82)

How to promote an already-built backend image to STAGING, and what the pipeline does and doesn't
do. See `staging-environment.md` for the environment itself (URLs, isolation, database) - this
document is about the release process on top of it.

## How to promote

GitHub Actions → **Deploy backend to STAGING** → **Run workflow**, giving the full 40-character
Git commit SHA of an image already built and pushed by `backend-ci.yml`'s `docker-build` job
(visible in that job's own summary, or as whatever SHA is currently running in DEV - `az
containerapp show --name ca-aiw-backend-dev --resource-group rg-aiw-dev-swc --query
"properties.template.containers[0].image"`).

No source rebuild happens in this workflow - there is no `docker build` step in it at all, only
`terraform apply -var backend_image_tag=<the given SHA>` against the already-pushed image. This
is what makes the promotion "the exact same immutable artifact previously built by CI" by
construction, not by discipline.

## What runs, in order

1. **Guard**: the given `image_tag` must look like a real 40-character commit SHA (rejects a
   branch name or `latest` before it ever reaches `terraform apply`).
2. **`terraform apply`** against `infrastructure/environments/staging` - updates the Container
   App's image reference. Azure Container Apps starts a new revision on that image.
3. **Migrations run automatically** as part of that new revision's own startup (Flyway, against
   the `aiw_staging` database only) - see `database-migrations.md` for the full mechanics; there
   is no separate "run migration" step in this workflow.
4. **Health check + smoke test** (`.github/actions/backend-smoke-test`, shared with DEV/PROD) -
   polls `/actuator/health` until `UP`, then a real API route.
5. A failed step at any point fails the whole workflow - **STAGING never reports success while
   actually broken**, and (AIW-83) PROD promotion requires this exact workflow to have
   succeeded for the given `image_tag` before it will run.

## Traceability

The job declares `environment: staging`, so every promotion shows up as a real GitHub Deployment
(who ran it, when, which commit, success/failure) under the repo's **Deployments** view - no
separate release-tracking system needed.

## What this pipeline deliberately does not do (yet)

- **Frontend**: STAGING's Static Web App (`staging-environment.md`) has no automated deploy
  workflow yet - it needs an `AZURE_STATIC_WEB_APPS_API_TOKEN_STAGING` GitHub secret provisioned
  first (the DEV equivalent, `..._DEV`, already exists; STAGING's own token has not been
  generated/stored yet). Out of scope for AIW-82, which is specifically the backend promotion
  path per its own AC wording.
- **Automatic triggering**: this workflow is `workflow_dispatch`-only, deliberately - a human
  decides when to promote, matching the "not automatically deployed" posture PROD's own AC
  (AIW-83) requires explicitly and STAGING shares by choice (unlike DEV, which does auto-deploy
  on every push).
