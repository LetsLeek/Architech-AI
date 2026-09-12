# Backup, restore and disaster recovery baseline (AIW-78)

The minimum recovery procedure for this platform: what backs up automatically, how to restore
it, how to rebuild an entire environment from scratch if it were lost outright, and who needs
what to actually do any of that. This document doesn't repeat every detail already documented
elsewhere - it ties `nonprod-database.md`, `prod-environment.md`, and each environment's own doc
together into one recovery-oriented narrative.

## PostgreSQL backup and point-in-time restore

Both the shared NONPROD server (`psql-aiw-nonprod-swc`, AIW-72) and PROD's dedicated server
(`psql-aiw-prod-swc`, AIW-76) are Azure Postgres Flexible Servers, which back up automatically -
no separate backup job or schedule to configure or maintain. Point-in-time recovery works within
the retention window with no separate toggle beyond `backup_retention_days`.

| | NONPROD | PROD |
|---|---|---|
| Retention | 7 days | 35 days (Azure's real maximum) |
| Geo-redundant | No | Yes |
| Databases | `aiw_dev`, `aiw_staging` | `aiw_prod` |

**Restore procedure** (both environments, same mechanism - a Flexible Server restore always
creates a **new** server, never restores in place):

```bash
az postgres flexible-server restore \
  --resource-group <rg-aiw-nonprod-swc | rg-aiw-prod-swc> \
  --name <new-server-name> \
  --source-server <psql-aiw-nonprod-swc | psql-aiw-prod-swc> \
  --restore-time <ISO 8601 timestamp within the retention window>
```

The restored server does **not** inherit the source's firewall rules - add one explicitly
(`az postgres flexible-server firewall-rule create ...`) before it's reachable for verification.
To actually cut the application over to a restored server: update the Key Vault's
`spring-datasource-url` secret to the restored server's new FQDN and re-apply that environment's
Terraform (or `az keyvault secret set` directly for a true emergency, with a Terraform-side
follow-up to reconcile state afterward).

### Real restore test performed (2026-09-12, NONPROD)

Per this ticket's own AC ("at least one non-production restore test is performed and
recorded") - not just documented as a procedure, actually run:

```bash
$ az postgres flexible-server restore \
    --resource-group rg-aiw-nonprod-swc \
    --name psql-aiw-restoretest-swc \
    --source-server psql-aiw-nonprod-swc \
    --restore-time "2026-09-12T15:57:13Z"
# ... "state": "Ready" ...

$ az postgres flexible-server firewall-rule create \
    --resource-group rg-aiw-nonprod-swc --server-name psql-aiw-restoretest-swc \
    --name terraform-admin-test --start-ip-address 91.115.38.199 --end-ip-address 91.115.38.199

$ psql "host=psql-aiw-restoretest-swc.postgres.database.azure.com ... dbname=aiw_dev user=aiw_dev_app sslmode=require" \
    -c "\dt" -c "SELECT current_database(), current_user, now();"
# All 11 real DEV tables present (agent_execution, project, flyway_schema_history, ...),
# connected successfully as the real dedicated aiw_dev_app role.

$ psql ... -c "SELECT count(*) FROM project;" \
      -c "SELECT version, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
# project_rows: 0 (no real project data has been created yet - this platform has no real usage
# yet, not a restore gap)
# flyway_schema_history: all 10 migrations present, each recorded "success = t"
```

**Result**: real, successful restore - schema, all 10 Flyway migrations, and the dedicated
least-privilege role all intact and queryable on the restored server. The temporary server and
its test firewall rule were deleted immediately after verification (`az postgres flexible-server
delete --name psql-aiw-restoretest-swc --yes`) to avoid ongoing cost - this was a verification
exercise, not a real incident.

## Rebuild path: Git + Terraform + container images

If an entire environment (or the whole subscription) were lost, the real rebuild path is:

1. **`infrastructure/bootstrap`** (local state only, see its own README) recreates the remote
   state storage account and GitHub Actions OIDC identity - the one piece that must exist before
   any other `terraform init` can even point at a backend.
2. **Each `environments/<env>` root**, in dependency order - `shared` (the ACR) before `nonprod`
   before `dev`/`staging` (both read `nonprod`'s state) before `prod` (self-contained). See
   `infrastructure/README.md`'s layout and each environment doc's own `terraform init`/`plan`/
   `apply` commands.
3. **Container images**: every image in `acraiwshared` is tagged with the exact Git commit SHA
   that built it (AIW-70's own tagging policy, never `latest`) - rebuilding from Git alone
   reproduces the identical image; nothing about a running deployment depends on an artifact that
   only exists in a registry with no corresponding source commit.
4. **Application data**: only PostgreSQL is stateful. Terraform recreates the server/database/
   role shells, but real data only comes back via the restore procedure above (from that
   environment's own backups) - Terraform apply alone does not, and structurally cannot, recreate
   lost rows.

Nothing in this path depends on a resource that isn't itself defined in this repository's
Terraform or built from its own Git history - the repository is the actual source of truth for
"what should exist," by construction, not merely by convention.

## Responsibilities and required credentials (no secrets exposed here)

| Task | Who | Needs |
|---|---|---|
| Run `terraform apply` against any environment | Whoever holds `Storage Blob Data Contributor` on the tfstate storage account + write access to that environment's resource group | `az login` as themselves - no shared credential, no secret in any config file (see `infrastructure/bootstrap/access.tf`) |
| Restore a PostgreSQL server | Same person/role as above, plus network access to the restored server's firewall-allowed IP | Nothing beyond their own Azure identity - restore doesn't need the database's own admin password |
| Read/rotate a Key Vault secret | Whoever holds `Key Vault Secrets Officer` on that environment's vault (currently: this Terraform apply's own caller) | Their own Azure identity - RBAC-only, no vault access policy list, no shared key |
| Redeploy the backend/frontend to an existing environment | Whoever can push to `develop` (backend image) or has the environment's `AZURE_STATIC_WEB_APPS_API_TOKEN_*` GitHub secret (frontend) | Standard repo write access - no infrastructure credential needed for a routine redeploy |

Every credential above is either a personal Azure AD identity (revocable independently, never
shared) or already-documented, RBAC-scoped access - nothing in this recovery path requires
distributing a shared secret to whoever performs the recovery.

## Multi-region / High Availability: explicitly future work

Neither NONPROD nor PROD runs multi-region or zone-redundant HA today - this project has no real
production traffic yet, so the added cost isn't justified by any current requirement (the same
reasoning already applied to AIW-76's HA decision). Azure Postgres Flexible Server supports
zone-redundant HA as an in-place upgrade whenever real uptime requirements justify it; a
multi-region setup would additionally need read replicas or geo-restore plus a second Container
Apps Environment (itself gated on the same subscription quota documented in
`prod-environment.md`). Both are real, available options to revisit later, not gaps in this
ticket's own scope.
