# Database migrations (AIW-84)

How Flyway migrations run across DEV, STAGING and PROD, and the constraints that apply to
writing them safely.

## How migrations actually run today

There is no separate "run migrations" CI step. `spring-boot-starter-flyway` +
`flyway-database-postgresql` are on the backend's classpath (`backend/pom.xml`), so every time
the Spring Boot application starts, Flyway runs any pending migrations under
`backend/src/main/resources/db/migration/` against whatever database
`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` point at (`spring.flyway.locations:
classpath:db/migration`, `application.yml`) - *before* the application context finishes starting,
so the app cannot serve traffic until migration either succeeds or fails.

This makes migrations environment-aware **by construction, not by extra CI logic**: each
environment's Container App resolves its own `SPRING_DATASOURCE_*` values from its own Key Vault
(DEV → `aiw_dev`, STAGING → `aiw_staging`, PROD → `aiw_prod` - see `dev-environment.md`/
`staging-environment.md`/`prod-environment.md`), so a DEV deploy can only ever migrate the DEV
database, a STAGING deploy only the STAGING database, and so on. There is no code path that lets
one environment's deploy touch another environment's schema.

## Migration ordering relative to application rollout

For every environment (`backend-ci.yml`'s `deploy-dev` job today; the STAGING/PROD promotion
workflows follow the identical shape - see `staging-deployment-pipeline.md`/
`prod-deployment-pipeline.md`):

1. `terraform apply` updates the Container App's image reference to the new tag.
2. Azure Container Apps starts a new revision running that image.
3. The new revision's process boots Spring Boot, which runs Flyway migration **before** the
   application context (and therefore the HTTP listener/health endpoints) comes up.
4. Only once migration succeeds and the app is listening does the revision become healthy;
   Container Apps then routes traffic to it (single-revision mode - see
   `rollback-runbook.md` for what that means for rollback).
5. The deploy workflow's own post-deployment health check (`GET /actuator/health`) is what
   actually observes step 3/4 finishing - **a migration failure surfaces as a health-check
   failure**, which fails the workflow per AIW-85's own AC. There is no separate
   "migration succeeded" signal to check; the app never becomes healthy if Flyway failed.

**Deployment fails clearly when a migration fails**: Flyway's own default behavior (unchanged,
no config overrides it) is to throw and prevent the Spring context from starting at all on a
failed migration - the container's readiness probe never turns healthy, the workflow's health
check loop times out, and the deploy workflow fails with that surfaced in its logs. Nothing masks
or retries past a bad migration automatically.

## Writing migrations safely (all environments, PROD especially)

**Backward compatibility / rollback constraints**: because a migration always runs *before* the
new application code that needs it, and because rollback (`rollback-runbook.md`) restores a
**previous image revision without reversing the schema**, every migration must be written so the
**previous** application version still runs correctly against the **post-migration** schema for
as long as that previous version might still be serving traffic (during a rolling deploy, or if a
rollback is later triggered). This is the standard **expand/contract** pattern:

- **Expand** (safe on its own, always ship first): add a new nullable column, add a new table,
  add a new index `CONCURRENTLY`-equivalent-safe operation. The old code ignores the new column;
  the new code can start using it once deployed.
- **Migrate/backfill**: a separate migration (or an explicit backfill step) populates the new
  structure from the old one, without removing the old one yet.
- **Contract** (only safe once nothing old-application-version depends on the removed thing
  anymore - i.e. only after a rollback to the pre-expand version is no longer a live
  possibility): drop the old column/table/constraint in a **later**, separate migration, never
  bundled into the same release as the expand step.

**Destructive schema changes require this explicit two-release strategy** - a single migration
that both adds a `NOT NULL` column with no default *and* is expected to work against
already-running old-version pods, or that drops a column the currently-deployed version still
reads, is not a safe migration regardless of environment; split it across the expand/contract
boundary above instead. This project has not yet needed a genuinely destructive migration (all
23 migrations to date, `V2`-`V23`, are additive - new tables/columns only) - this section exists
so the first one that needs it followed a decided policy rather than an improvised one.

**No manual Azure Portal schema edits** for a normal release: every schema change goes through a
numbered `V<n>__description.sql` file, reviewed in the same PR as the application code that needs
it, and applied only by Flyway on the next deploy - never a manual `psql`/Portal query editor
change against DEV, STAGING or PROD. The one documented exception is the restore procedure in
`prod-environment.md`'s "Restore procedure" section, which is a disaster-recovery operation, not
a schema *change*.
