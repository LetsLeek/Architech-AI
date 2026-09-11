# Terraform state bootstrap (AIW-69)

Creates the two things every other Terraform config in `infrastructure/` depends on, and which
therefore cannot themselves be created by a config using the remote backend they provide:

- `rg-aiw-tfstate-swc` - a dedicated resource group, deliberately separate from `rg-aiw-shared-swc`
  (which holds actual shared *application* resources, e.g. the ACR from AIW-70), so the real
  `environments/shared` config never has to import or co-manage this bootstrap's own resources.
- `staiwtfstateswc` - the storage account holding every environment's remote state, one blob per
  environment (`shared.tfstate`, `dev.tfstate`, `staging.tfstate`, `prod.tfstate`), each isolated
  by name within the same `tfstate` container. Access is Azure AD/RBAC only
  (`shared_access_key_enabled = false`) - no storage account key exists to leak.

This config also creates the GitHub Actions OIDC identity (`access.tf`) that lets CI run
`terraform fmt`/`validate`/`plan` without any committed secret, per AIW-69's own acceptance
criterion - see that file's comments for the exact scope (Reader + state-container access only,
never `apply`-capable).

## Why local state here, and only here

This is the one deliberate exception to "every Terraform config uses the Azure Storage backend"
in this whole tree, chosen over an alternative like a manually-run `az` CLI script, because it
keeps the bootstrap resources themselves declaratively defined and reproducible (re-run this
config, not a from-memory sequence of `az` commands, if the bootstrap resources are ever lost).
The resulting `terraform.tfstate` in this directory is real and does matter - it is gitignored
(see `infrastructure/.gitignore`), never committed - but losing it only means re-importing two
resources and a handful of role assignments by hand, not losing any application data.

## One-time run procedure

```bash
cd infrastructure/bootstrap
az login   # if not already logged in
terraform init
terraform plan    # review before applying - this creates real, billable Azure resources
terraform apply
```

After a successful apply, take the outputs and:

1. Use `resource_group_name` / `storage_account_name` / `container_name` as the
   `-backend-config` values when running `terraform init` in any `environments/*` directory (see
   `infrastructure/README.md`).
2. Set `github_actions_client_id`, `azure_tenant_id`, and `azure_subscription_id` as GitHub
   Actions **repository variables** (not secrets - none of these three values are sensitive on
   their own; OIDC is exactly what makes that true) for `.github/workflows/terraform-ci.yml` to
   use.

Re-running `apply` after the first time is idempotent (standard Terraform behavior) - this isn't
a strictly one-time-ever script, just a config that's expected to change rarely.
