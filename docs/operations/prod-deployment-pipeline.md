# PRODUCTION deployment pipeline (AIW-83)

See `prod-environment.md` for the environment itself (currently blocked on the documented
Container Apps Environment quota increase - this pipeline is fully built and ready, but cannot
actually run to completion until that's unblocked). This document is the release process on top
of it, once it is.

## Approval gate

The **production** GitHub Environment (`gh api repos/LetsLeek/Architech-AI/environments/production`)
has a `required_reviewers` protection rule naming the repo owner (`LetsLeek`) as the required
reviewer. GitHub does not support declaring environment protection rules inside workflow YAML -
they're configured via the API/UI against the environment itself, separately from
`backend-deploy-prod.yml`.

**Honest limitation, not hidden**: this is currently a one-person project, so the required
reviewer and the person who can dispatch the workflow are the same account, and GitHub Environments
have `can_admins_bypass: true` by default for a repo admin. The approval step still provides real
value - it forces an explicit, separately-logged "yes, deploy this" action distinct from clicking
"Run workflow," and the mechanism is ready to add a second, independent reviewer the moment this
stops being a one-person project, with no workflow change needed. It is not, today, a control
that prevents the account owner from single-handedly deploying to PROD.

## How to promote

1. Confirm the target `image_tag` already has a **successful** STAGING deployment
   (`staging-deployment-pipeline.md`) - the workflow's own `verify-staging` job checks this via
   the GitHub Deployments API before a reviewer is ever asked to approve anything, and fails
   fast with a clear message if it hasn't.
2. GitHub Actions → **Deploy backend to PRODUCTION** → **Run workflow**, giving that same
   `image_tag`.
3. The `deploy` job pauses at its `environment: production` gate. The required reviewer approves
   the run (GitHub Actions run page → Review deployments).
4. Once approved: `terraform apply` against `infrastructure/environments/prod` (no rebuild, same
   "promote the exact artifact" shape as STAGING), migrations run automatically at application
   startup against `aiw_prod` only (`database-migrations.md`), then the shared health-check/
   smoke-test action (AIW-85).
5. A failed health check/smoke test fails the workflow with a clear annotation - PROD does not
   auto-rollback on failure; that's a deliberate, separate human action (`rollback-runbook.md`),
   matching STAGING's own rollback design.

## Why production is never deployed on every commit

`backend-deploy-prod.yml` has no `push`/`pull_request` trigger at all - `workflow_dispatch`
only, with the STAGING-validation gate ahead of the reviewer-approval gate ahead of the actual
apply. Three independent things all have to be true before anything touches PROD: a human
explicitly triggers it, that exact commit already passed STAGING, and a reviewer approves it.

## Access scoping

The GitHub Actions OIDC identity's PROD write access (`infrastructure/bootstrap/access.tf`'s
`github_actions_prod_deploy` role assignment, added in AIW-82 to avoid touching `access.tf`
twice) is scoped to `rg-aiw-prod-swc` only - the same identity that can write to DEV/STAGING has
no broader reach into PROD than that one resource group, matching `prod-environment.md`'s own
documented least-privilege RBAC for every other PROD credential.
