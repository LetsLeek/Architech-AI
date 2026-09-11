# Infrastructure (AIW-69)

Terraform for the Architech AI **platform's own** Azure infrastructure - not the infrastructure
that will eventually host customer-generated websites (see
[`docs/operations/azure-environment-architecture.md`](../docs/operations/azure-environment-architecture.md)'s
own "Platform environments vs. customer website environments" section for that boundary).

## Layout

```
infrastructure/
  bootstrap/        # one-time (rarely re-run): remote state storage account + CI OIDC identity.
                     # Uses LOCAL state deliberately - see bootstrap/README.md for why.
  modules/           # reusable building blocks, one per Azure resource type. Environment roots
                     # instantiate these; a module never hardcodes an environment-specific value.
    resource-group/
    container-registry/         # AIW-70 fills in real usage
    container-app-environment/  # AIW-71/74/75
    container-app/              # AIW-71/74/75
    postgresql/                 # AIW-72/76
    key-vault/                  # AIW-73
    monitoring/                 # AIW-71/74/75 (workspace) + AIW-77 (alerts)
    networking/                 # AIW-76 (PROD private connectivity)
  environments/
    shared/    # rg-aiw-shared-swc - cross-environment shared resources (e.g. the ACR)
    dev/       # rg-aiw-dev-swc
    staging/   # rg-aiw-staging-swc
    prod/      # rg-aiw-prod-swc, always isolated from the other three
```

Each `environments/<env>/` directory is its own Terraform root module with its own state file -
never a single combined state across environments, per this ticket's own acceptance criterion
("environment states are isolated").

## Remote state

All four environment roots use the `azurerm` backend, authenticated via Azure AD
(`use_azuread_auth = true`, no storage account key exists to configure) against the storage
account `infrastructure/bootstrap` creates. The backend block in each `versions.tf` is
deliberately left without a hardcoded storage account name - supply it at `init` time:

```bash
cd infrastructure/environments/<env>
terraform init \
  -backend-config="resource_group_name=rg-aiw-tfstate-swc" \
  -backend-config="storage_account_name=staiwtfstateswc" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=<env>.tfstate"
```

(`bootstrap`'s own outputs give you the first three values verbatim if the resource group/storage
account names ever change from the defaults above.)

## Adding a new module or environment resource

1. If the Azure resource type doesn't have a module under `modules/` yet, add one there first -
   `variables.tf` + `main.tf` + `outputs.tf`, no environment-specific values inside the module
   itself.
2. Instantiate it from the owning ticket's environment `main.tf` (e.g. AIW-71 adds a
   `container-app-environment` + `container-app` block to `environments/dev/main.tf`), passing
   environment-specific values (names following the convention in
   `docs/operations/azure-environment-architecture.md`, the right tags) as module arguments.
3. Never instantiate a module directly in more than one environment's `main.tf` pointing at the
   same underlying resource - each environment's copy is a distinct resource, by design (see the
   architecture doc's isolation rules).

## CI

`.github/workflows/terraform-ci.yml` runs `terraform fmt -check`, `validate`, and `plan` (never
`apply`) on pull requests that touch `infrastructure/`, authenticated via the OIDC identity
`infrastructure/bootstrap/access.tf` creates - no client secret stored in GitHub, per this
ticket's own acceptance criterion.
