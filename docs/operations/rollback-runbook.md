# Rollback runbook (AIW-86)

## Container Apps revision strategy

Every environment's Container App runs in `revision_mode = "Single"`
(`infrastructure/modules/container-app/main.tf`): only one revision receives traffic at a time,
and a new `terraform apply` (a promotion, AIW-82/AIW-83) creates a new revision and shifts 100%
of traffic to it automatically. Azure Container Apps does **not** delete the previous revision
when this happens - it becomes *inactive* but still exists, still references its own (older)
image, and can be reactivated.

This is what makes rollback possible without rebuilding anything: **every deployed application
version is traceable to an immutable revision, which is itself pinned to an immutable image
tag/digest** (`docker-build`'s SHA tag, AIW-80) - `az containerapp revision list --name
<app> --resource-group <rg> -o table` shows every revision still known to Azure, each with its
own `image` column.

**Restoring a previous version never rebuilds source** - `backend-rollback.yml` only calls `az
containerapp revision activate` (reactivates the old revision as-is) and `az containerapp
ingress traffic set` (shifts traffic to it) - no `terraform apply`, no `docker build`, nothing
that touches source or creates a new artifact.

### Single revision mode today vs. blue/green/canary later

Single mode is deliberately the current choice - one active revision, one clear "what's live"
answer, minimal operational complexity for this project's current size. Azure Container Apps'
`revision_mode = "Multiple"` is the documented upgrade path once a real need for it exists: it
allows several revisions to be simultaneously active with an explicit traffic-weight split
across them (e.g. 90/10 canary, or a blue/green pair briefly running side by side before cutting
over) - the exact same `azurerm_container_app.template`/`ingress.traffic_weight` shape this
module already declares, just with more than one `traffic_weight` block active. Revisiting this
is a deliberate, future, need-driven decision (matching this project's established "don't build
ahead of a real requirement" posture, e.g. `prod-environment.md`'s own High Availability
section) - not something this ticket forces a premature choice on.

## Application rollback vs. database rollback

These are two **separate procedures**, not one combined "rollback":

| | Application rollback | Database rollback |
|---|---|---|
| **What it undoes** | Which code is serving traffic | Schema/data changes |
| **Mechanism** | `backend-rollback.yml` - reactivate a prior Container Apps revision, shift traffic | Restore-from-backup (`prod-environment.md`'s own "Restore procedure") or a hand-written compensating migration |
| **Speed** | Seconds (traffic-weight change only) | Minutes-to-hours (Flexible Server restore creates a **new** server) |
| **How often needed** | Whenever a bad release needs to be pulled back quickly | Rare - only when a migration itself was destructive/wrong, not just when application code had a bug |

**Why they're separate**: `database-migrations.md`'s expand/contract policy exists specifically
so that rolling back the *application* (to a version from before a migration) still works
correctly against the *post-migration* schema, without needing to also roll back the database in
the common case. A database rollback is the exception path for when a migration itself was the
problem - not the default response to "the last release was bad."

## When rollback should be triggered (production)

Trigger an application rollback (once PROD's own rollback workflow exists, per AIW-83's own
scope) when:

- Post-deployment health check/smoke test (AIW-85) fails **after** promotion already completed
  (a failure *during* promotion, before traffic shifts, already leaves the previous revision
  serving - see `staging-deployment-pipeline.md`'s "failed validation blocks promotion"), or
- Real user-facing errors/elevated error rate appear shortly after a release that correlate with
  the deploy, and the fix is not yet ready to ship forward.

Do **not** default to a database rollback for a bad release - only escalate to it if the
migration itself (not the application code) is confirmed to be the actual problem, per the
distinction above.

## Non-production rollback test

**Status: documented and workflow-built, not yet executed.** Per this session's explicit
instruction, deployment pipelines are being built without being run against real STAGING/PROD
infrastructure - so the actual test run this AC calls for (deploy to STAGING, roll back via
`backend-rollback.yml`, verify the smoke test passes post-rollback) is deferred until a real
STAGING promotion is deliberately triggered. Once that happens, the procedure is:

```bash
# 1. Note the currently-active revision before promoting again
az containerapp revision list --name ca-aiw-backend-staging --resource-group rg-aiw-staging-swc -o table

# 2. Promote a new image tag (backend-deploy-staging.yml, or the CLI equivalent in
#    staging-deployment-pipeline.md), creating a new active revision.

# 3. Roll back to the revision noted in step 1 via backend-rollback.yml (GitHub Actions →
#    "Roll back backend (STAGING)" → Run workflow → target_revision from step 1).

# 4. Confirm the workflow's own post-rollback smoke test passed, and that
#    `az containerapp revision list` shows 100% traffic back on the step-1 revision.
```

This runbook should be updated with the real result (date, revision names, outcome) once that
test is actually run - this section intentionally does not claim it already happened.
