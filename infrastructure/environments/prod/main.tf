# Foundation (AIW-69) plus real PROD runtime resources (AIW-75) - this resource group is, by
# construction, never shared with DEV/STAGING (see azure-environment-architecture.md's isolation
# rules). AIW-76 (dedicated PROD PostgreSQL with private connectivity/backups) still owns the
# database itself and the resulting Key Vault secrets/Container App secret wiring - this ticket's
# own scope stops at standing up the isolated identity/vault/hosting shell those will attach to,
# same sequencing precedent as AIW-71 (DEV runtime) preceding AIW-72 (DEV database)/AIW-73 (DEV
# secret wiring).
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-prod-swc"
  location = "Sweden Central"
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

module "monitoring" {
  source = "../../modules/monitoring"

  name                = "log-aiw-prod-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# NOT YET APPLIED (AIW-75): this subscription has a real, confirmed hard quota of exactly 1
# Container App Environment total (MaxNumberOfGlobalEnvironmentsInSubExceeded - see AIW-74's
# real discovery, documented in azure-environment-architecture.md). DEV/STAGING already occupy
# that one environment - reusing it for PROD would violate this ticket's own explicit isolation
# requirement ("stronger isolation and access controls than non-production") and the "never
# extended to PROD" boundary already established for every other shared-non-prod exception in
# this project. This block is real, intended, promotion-ready configuration - left un-applied
# (pinned) until a real Azure quota increase is requested and granted (a support-ticket process,
# not something a Terraform apply or this agent can do), not a placeholder guess. See
# docs/operations/prod-environment.md for the exact request procedure.
module "container_app_environment" {
  source = "../../modules/container-app-environment"

  name                       = "cae-aiw-prod-swc"
  resource_group_name        = module.resource_group.name
  location                   = module.resource_group.location
  log_analytics_workspace_id = module.monitoring.id
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# Looked up by its known name/resource group (AIW-70's own real, stable values) - same reasoning
# as environments/dev's own data source.
data "azurerm_container_registry" "shared" {
  name                = "acraiwshared"
  resource_group_name = "rg-aiw-shared-swc"
}

# User-assigned (not system-assigned) - same reasoning as DEV/STAGING's own backend identity.
# Real and applied now, independent of the Container Apps Environment quota block above.
resource "azurerm_user_assigned_identity" "backend" {
  name                = "id-aiw-backend-prod"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "azurerm_role_assignment" "backend_acr_pull" {
  scope                = data.azurerm_container_registry.shared.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.backend.principal_id
}

data "azurerm_client_config" "current" {}

module "key_vault" {
  source = "../../modules/key-vault"

  name                = "kv-aiw-prod-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tenant_id           = data.azurerm_client_config.current.tenant_id

  # Deny-all-by-default plus the two identities that need data-plane access: Azure services (so
  # the PROD Container App's own managed identity, once AIW-76 wires real secrets into it, can
  # reach the vault) and this apply's own current caller - same "terraform-admin" pattern as
  # every other environment's vault.
  allowed_ip_ranges = ["91.115.38.199"]

  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "azurerm_role_assignment" "terraform_admin_kv_secrets_officer" {
  scope                = module.key_vault.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_role_assignment" "backend_kv_secrets_user" {
  scope                = module.key_vault.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.backend.principal_id
}

# NOT YET APPLIED (AIW-75): depends on the Container Apps Environment above. No
# azurerm_key_vault_secret/key_vault_secrets wiring exists yet either - AIW-76 owns the real PROD
# PostgreSQL server those secrets come from (mirrors AIW-71 preceding AIW-72/AIW-73 for DEV).
# Production does not, and per this ticket's own AC never will, read a DEV/STAGING database or
# secret - every value this module ends up wired to comes from PROD's own Key Vault only.
module "backend" {
  source = "../../modules/container-app"

  name                         = "ca-aiw-backend-prod"
  resource_group_name          = module.resource_group.name
  container_app_environment_id = module.container_app_environment.id
  user_assigned_identity_id    = azurerm_user_assigned_identity.backend.id
  registry_server              = data.azurerm_container_registry.shared.login_server

  # Same image artifact model as DEV/STAGING (AIW-68's own contract): the identical image,
  # promoted unchanged, tagged with the Git commit SHA that STAGING last verified.
  image = "${data.azurerm_container_registry.shared.login_server}/architech-backend:${var.backend_image_tag}"

  env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "ARCHITECH_CORS_ALLOWEDORIGINS", value = "https://${module.frontend.default_host_name}" },
  ]

  # AIW-76 populates these once PROD's own PostgreSQL server/secrets exist.
  key_vault_secrets = []
  secret_env        = []

  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }

  depends_on = [azurerm_role_assignment.backend_acr_pull, azurerm_role_assignment.backend_kv_secrets_user]
}

module "frontend" {
  source = "../../modules/static-web-app"

  name                = "swa-aiw-frontend-prod"
  resource_group_name = module.resource_group.name
  # Deliberately NOT module.resource_group.location - Static Web Apps aren't available in Sweden
  # Central; Central US is this module's own real, verified default (see
  # infrastructure/modules/static-web-app/variables.tf).
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
