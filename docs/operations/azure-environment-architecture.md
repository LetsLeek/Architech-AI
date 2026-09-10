# Azure environment architecture (AIW-67)

The target environment model for the Architech AI **platform itself** - not the customer
websites it produces (see [Platform environments vs. customer website
environments](#platform-environments-vs-customer-website-environments) below, which this
document deliberately does not cover).

## Environments

Three environments, strictly ordered: **DEV → STAGING → PROD**.

| Environment | Purpose | Who touches it |
|---|---|---|
| DEV | Active development, integration of merged `develop` changes, manual exploration | Engineers, continuously |
| STAGING | Pre-production verification against production-like config/scale | Engineers/QA, before each release |
| PROD | Real customer traffic | End users; engineers only via deploy/observability tooling |

Every platform environment (backend, database, secrets, configuration) is fully isolated per
row above - nothing in this document describes a resource shared *between* DEV, STAGING, and
PROD, only resources shared *within* the platform across environments (see [Shared vs.
environment-specific resources](#shared-vs-environment-specific-resources)).

## Environment isolation rules

- **App:** each environment gets its own Container App instance, in its own resource group.
  The same container image is promoted DEV → STAGING → PROD unchanged (see AIW-68); only the
  environment-specific configuration injected into it differs. No environment's app instance
  ever serves another environment's traffic.
- **Database:** PROD gets its own PostgreSQL Flexible Server, with network/firewall rules and
  an admin credential that exist only for PROD. DEV and STAGING may share a single non-prod
  server (separate databases on it) purely as a cost optimization - an explicit, documented
  exception, never extended to PROD. **Production data never exists outside the PROD
  server**, by construction: nothing copies or seeds PROD data into DEV/STAGING.
- **Secrets:** each environment gets its own Azure Key Vault (see
  [`secret-management.md`](secret-management.md) for how the backend consumes it). PROD's Key
  Vault is reachable only by PROD's own managed identity - a DEV or STAGING credential/identity
  has no access path to a PROD secret, so a production API key or database password **cannot
  be reused by non-production by design**, not merely by convention.
- **Configuration:** environment-specific values (API keys, database connection strings,
  feature flags) are injected as environment variables at deploy time from that environment's
  own Key Vault (Container App secret references - see `secret-management.md`'s decision).
  Nothing environment-specific is ever baked into the container image itself.

## Azure resource groups and naming conventions

Pattern: `<resource-type-abbreviation>-aiw-<scope>-<region>`, all lowercase, hyphen-separated
except where an Azure resource type forbids hyphens (Container Registry, Storage Account -
noted below). `aiw` is this platform's short workload token (matches the Jira project key).
`<scope>` is `shared`, `dev`, `staging`, or `prod`. `<region>` is the short Azure region code;
`weu` (West Europe) is the assumed primary region below - revisit if actual latency/compliance
requirements point elsewhere, this is a config decision, not a fixed constraint of the naming
scheme itself.

| Resource | Naming pattern | Example (DEV) |
|---|---|---|
| Resource group | `rg-aiw-<scope>-<region>` | `rg-aiw-dev-weu` |
| Container Apps Environment | `cae-aiw-<scope>-<region>` | `cae-aiw-dev-weu` |
| Container App (backend) | `ca-aiw-backend-<scope>` | `ca-aiw-backend-dev` |
| Container Registry (shared, AIW-70) | `acraiwshared` (no hyphens - ACR names are alphanumeric-only) | `acraiwshared` |
| Key Vault | `kv-aiw-<scope>-<region>` | `kv-aiw-dev-weu` |
| PostgreSQL Flexible Server | `psql-aiw-<scope>-<region>` | `psql-aiw-dev-weu` |
| Log Analytics workspace | `log-aiw-<scope>-<region>` | `log-aiw-dev-weu` |
| Storage account (if ever needed) | `staiw<scope><region>` (no hyphens, ≤24 lowercase alphanumeric chars) | `staiwdevweu` |

Every resource carries these tags, usable directly as Terraform `tags = {}` blocks:

```hcl
tags = {
  environment = "dev"        # dev | staging | prod | shared
  workload    = "aiw"
  managed-by  = "terraform"
}
```

## Shared vs. environment-specific resources

- **Shared across all platform environments:** the Container Registry
  (`rg-aiw-shared-weu` / `acraiwshared`, AIW-70) - one registry holds every environment's
  images, distinguished by tag, not by a separate registry per environment. This is the only
  resource this document currently designates as cross-environment shared.
- **Environment-specific (one per DEV/STAGING/PROD):** Container Apps Environment, Container
  App, Key Vault, PostgreSQL Flexible Server, Log Analytics workspace - each lives in that
  environment's own resource group (`rg-aiw-<scope>-<region>`) and is never referenced by
  another environment's resources.

## Subscription model

**Now:** a single Azure subscription holds every resource group above (`shared`, `dev`,
`staging`, `prod`). Isolation between environments relies on resource-group boundaries, RBAC
role assignments scoped per resource group, and the naming/tagging convention above - acceptable
at this stage given the platform's current scale and the absence of real customer data in PROD
yet.

**Future path (not yet executed):** split into two subscriptions once PROD carries real
customer data or traffic that justifies the stronger isolation a subscription boundary
provides (separate billing, separate default RBAC root, contained blast radius for a
misconfigured policy or role assignment):

- `architech-nonprod` - `shared`, `dev`, and `staging` resource groups.
- `architech-prod` - the `prod` resource group only.

Moving `prod` into its own subscription is a resource-group-level move (Azure supports moving
resource groups between subscriptions), not a rebuild - the naming convention above already
treats `prod` as fully self-contained, which is what makes that later move straightforward.

## Platform environments vs. customer website environments

This document covers only the Architech AI **platform's own** DEV/STAGING/PROD - the backend
and frontend that let a customer create a project and run Requirements Analysis. It
deliberately does **not** cover the (future) infrastructure that hosts a customer's *generated
website* in preview or production - that is a different concern with a different shape
(likely provisioned dynamically per customer/project by the platform itself, not a small,
fixed set of environments managed by hand via Terraform the way this document's resources
are). Naming/tagging/resource-group conventions for customer website hosting are out of scope
here and should get their own decision once that feature exists - don't reuse `rg-aiw-*`
naming for it, to keep the two concerns unambiguous in the Azure portal and in cost reporting.
