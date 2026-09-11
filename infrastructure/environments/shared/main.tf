# Foundation (AIW-69) plus real shared resources as their owning tickets land.
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-shared-swc"
  location = "Sweden Central"
  tags = {
    environment = "shared"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# AIW-70: the one registry every environment's Container App pulls from (per
# docs/operations/azure-environment-architecture.md's "Shared vs. environment-specific
# resources" - ACR is the only cross-environment-shared resource by design), distinguishing
# images by tag rather than by a separate registry per environment.
module "container_registry" {
  source = "../../modules/container-registry"

  name                = "acraiwshared"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  sku                 = "Basic"
  tags = {
    environment = "shared"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# The existing GitHub Actions OIDC identity (infrastructure/bootstrap/access.tf) needs push
# access to publish images from CI - looked up by its known display name rather than a
# hardcoded object ID, so this file doesn't silently go stale if that identity is ever
# recreated. AcrPush only (not AcrPull too - Azure's AcrPush role already includes pull), and
# scoped to this one registry, never subscription-wide.
data "azuread_service_principal" "github_actions" {
  display_name = "aiw-github-actions-terraform"
}

resource "azurerm_role_assignment" "github_actions_acr_push" {
  scope                = module.container_registry.id
  role_definition_name = "AcrPush"
  principal_id         = data.azuread_service_principal.github_actions.object_id
}

# Runtime pull access (AcrPull) is granted per-environment, to that environment's own Container
# App managed identity, once AIW-71/74/75 actually create those identities - granting it here
# ahead of time would have nothing real to scope it to.
