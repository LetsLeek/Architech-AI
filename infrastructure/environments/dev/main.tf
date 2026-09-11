# Foundation (AIW-69) plus real DEV runtime resources (AIW-71). AIW-72/AIW-73 add the shared
# NONPROD PostgreSQL and Key Vault wiring - this file's env vars deliberately don't set
# SPRING_DATASOURCE_* yet, since no real DB or secret-management mechanism exists to source
# real values from until those land (see the Container App block below for the honest gap this
# leaves in the backend's own health check, documented rather than papered over).
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-dev-swc"
  location = "Sweden Central"
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

module "monitoring" {
  source = "../../modules/monitoring"

  name                = "log-aiw-dev-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

module "container_app_environment" {
  source = "../../modules/container-app-environment"

  name                       = "cae-aiw-dev-swc"
  resource_group_name        = module.resource_group.name
  location                   = module.resource_group.location
  log_analytics_workspace_id = module.monitoring.id
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# Looked up by its known name/resource group (AIW-70's own real, stable values), not passed via
# a Terraform remote-state data source - avoids granting this environment's state read access to
# shared's state file just to learn one login server string.
data "azurerm_container_registry" "shared" {
  name                = "acraiwshared"
  resource_group_name = "rg-aiw-shared-swc"
}

# User-assigned (not system-assigned) so it can be granted AcrPull below and referenced by the
# Container App in the same apply, with no create-order ambiguity between "the identity exists"
# and "the thing that needs it exists" - the same reasoning documented on the container-app
# module's own user_assigned_identity_id variable.
resource "azurerm_user_assigned_identity" "backend" {
  name                = "id-aiw-backend-dev"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "azurerm_role_assignment" "backend_acr_pull" {
  scope                = data.azurerm_container_registry.shared.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.backend.principal_id
}

module "backend" {
  source = "../../modules/container-app"

  name                         = "ca-aiw-backend-dev"
  resource_group_name          = module.resource_group.name
  container_app_environment_id = module.container_app_environment.id
  user_assigned_identity_id    = azurerm_user_assigned_identity.backend.id
  registry_server              = data.azurerm_container_registry.shared.login_server

  # The real image AIW-70's CI pushes on every merge to develop, tagged with that merge
  # commit's own SHA - built and pushed once by hand for this ticket's own first real apply
  # (see the PR description for the exact SHA/command), since no push-to-develop event has
  # happened yet to trigger the normal CI path for this brand-new registry.
  image = "${data.azurerm_container_registry.shared.login_server}/architech-backend:${var.backend_image_tag}"

  env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "dev" },
    # Spring Boot relaxed binding: this env var name binds to architech.cors.allowed-origins
    # (core.web.CorsProperties) - the real DEV frontend's own origin, so the browser's
    # same-origin policy allows the deployed Static Web App to call this backend's /api/**.
    { name = "ARCHITECH_CORS_ALLOWEDORIGINS", value = "https://${module.frontend.default_host_name}" },
  ]

  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }

  depends_on = [azurerm_role_assignment.backend_acr_pull]
}

module "frontend" {
  source = "../../modules/static-web-app"

  name                = "swa-aiw-frontend-dev"
  resource_group_name = module.resource_group.name
  # Deliberately NOT module.resource_group.location - Static Web Apps aren't available in
  # Sweden Central, and this subscription's West Europe restriction rules out the obvious EU
  # fallback too; Central US is this module's own real, verified default. See
  # infrastructure/modules/static-web-app/variables.tf and
  # docs/operations/azure-environment-architecture.md for the full explanation.
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
