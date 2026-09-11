# Foundation (AIW-69) plus real STAGING runtime resources (AIW-74) - mirrors environments/dev's
# own structure (AIW-71/AIW-73), reusing the shared NONPROD PostgreSQL server from AIW-72 (a
# separate `aiw_staging` database/role on it, per docs/operations/azure-environment-architecture.md's
# documented DEV/STAGING cost-optimization exception - never extended to PROD).
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-staging-swc"
  location = "Sweden Central"
  tags = {
    environment = "staging"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

# Real, empirically-discovered subscription quota (AIW-74): this subscription allows only 1
# Container App Environment *total* (not just per-region - a first attempt at a second
# region-distinct one still hit MaxNumberOfGlobalEnvironmentsInSubExceeded). STAGING therefore
# reuses DEV's own cae-aiw-dev-swc rather than provisioning a separate environment/Log Analytics
# workspace - the same shared-non-prod-infra exception already established for the NONPROD
# PostgreSQL server (AIW-72's own documented DEV/STAGING cost-optimization exception, never
# extended to PROD). Container Apps within a shared environment are still fully separate
# resources with their own FQDNs/ingress/scaling - this is a networking/logging boundary only,
# not a traffic-sharing one.
data "terraform_remote_state" "dev" {
  backend = "azurerm"
  config = {
    resource_group_name  = "rg-aiw-tfstate-swc"
    storage_account_name = "staiwtfstateswc"
    container_name       = "tfstate"
    key                  = "dev.tfstate"
    use_azuread_auth     = true
  }
}

# Looked up by its known name/resource group (AIW-70's own real, stable values), same reasoning
# as environments/dev's own data source - avoids granting this environment's state read access to
# shared's state file just to learn one login server string.
data "azurerm_container_registry" "shared" {
  name                = "acraiwshared"
  resource_group_name = "rg-aiw-shared-swc"
}

# User-assigned (not system-assigned) so it can be granted AcrPull/Key Vault Secrets User below
# and referenced by the Container App in the same apply, with no create-order ambiguity - same
# reasoning as environments/dev's own backend identity.
resource "azurerm_user_assigned_identity" "backend" {
  name                = "id-aiw-backend-staging"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tags = {
    environment = "staging"
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

# AIW-72's real STAGING database/role, read from its own state file - not re-derived or
# duplicated here. Read-only: this environment never writes to nonprod's state, only reads its
# outputs.
data "terraform_remote_state" "nonprod" {
  backend = "azurerm"
  config = {
    resource_group_name  = "rg-aiw-tfstate-swc"
    storage_account_name = "staiwtfstateswc"
    container_name       = "tfstate"
    key                  = "nonprod.tfstate"
    use_azuread_auth     = true
  }
}

module "key_vault" {
  source = "../../modules/key-vault"

  name                = "kv-aiw-staging-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tenant_id           = data.azurerm_client_config.current.tenant_id

  # Deny-all-by-default plus the two identities that actually need data-plane access: Azure
  # services (so the STAGING Container App's own managed identity, reading secrets at runtime,
  # can reach the vault) and this apply's own current caller - same "terraform-admin" pattern
  # AIW-72/AIW-73 already established.
  allowed_ip_ranges = ["91.115.38.199"]

  tags = {
    environment = "staging"
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

# The real values AIW-72 generated (random_password resources in nonprod's own state) - never a
# literal value in this file. Same AC as AIW-73: no application/API/database secrets are
# committed to Git or baked into images.
resource "azurerm_key_vault_secret" "spring_datasource_url" {
  name            = "spring-datasource-url"
  value           = "jdbc:postgresql://${data.terraform_remote_state.nonprod.outputs.postgresql_fqdn}:5432/${data.terraform_remote_state.nonprod.outputs.staging_database_name}?sslmode=require"
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_username" {
  name            = "spring-datasource-username"
  value           = data.terraform_remote_state.nonprod.outputs.staging_app_username
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_password" {
  name            = "spring-datasource-password"
  value           = data.terraform_remote_state.nonprod.outputs.staging_app_password
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

module "backend" {
  source = "../../modules/container-app"

  name                         = "ca-aiw-backend-staging"
  resource_group_name          = module.resource_group.name
  container_app_environment_id = data.terraform_remote_state.dev.outputs.container_app_environment_id
  user_assigned_identity_id    = azurerm_user_assigned_identity.backend.id
  registry_server              = data.azurerm_container_registry.shared.login_server

  # Same image artifact model as DEV/PROD (AIW-68's own contract): the identical image, promoted
  # unchanged, tagged with the Git commit SHA that CI last verified - only the env/secret wiring
  # below differs per environment.
  image = "${data.azurerm_container_registry.shared.login_server}/architech-backend:${var.backend_image_tag}"

  env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "staging" },
    # Spring Boot relaxed binding: binds to architech.cors.allowed-origins (core.web.CorsProperties)
    # - the real STAGING frontend's own origin, so the browser's same-origin policy allows the
    # deployed Static Web App to call this backend's /api/**.
    { name = "ARCHITECH_CORS_ALLOWEDORIGINS", value = "https://${module.frontend.default_host_name}" },
  ]

  key_vault_secrets = [
    { name = "spring-datasource-url", key_vault_secret_id = azurerm_key_vault_secret.spring_datasource_url.versionless_id },
    { name = "spring-datasource-username", key_vault_secret_id = azurerm_key_vault_secret.spring_datasource_username.versionless_id },
    { name = "spring-datasource-password", key_vault_secret_id = azurerm_key_vault_secret.spring_datasource_password.versionless_id },
  ]
  secret_env = [
    { name = "SPRING_DATASOURCE_URL", secret_name = "spring-datasource-url" },
    { name = "SPRING_DATASOURCE_USERNAME", secret_name = "spring-datasource-username" },
    { name = "SPRING_DATASOURCE_PASSWORD", secret_name = "spring-datasource-password" },
  ]

  tags = {
    environment = "staging"
    workload    = "aiw"
    managed-by  = "terraform"
  }

  depends_on = [azurerm_role_assignment.backend_acr_pull, azurerm_role_assignment.backend_kv_secrets_user]
}

module "frontend" {
  source = "../../modules/static-web-app"

  name                = "swa-aiw-frontend-staging"
  resource_group_name = module.resource_group.name
  # Deliberately NOT module.resource_group.location - Static Web Apps aren't available in Sweden
  # Central; Central US is this module's own real, verified default (see
  # infrastructure/modules/static-web-app/variables.tf).
  tags = {
    environment = "staging"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
