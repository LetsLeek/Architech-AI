# Foundation (AIW-69) plus real DEV runtime resources (AIW-71), the shared NONPROD PostgreSQL
# (AIW-72), and Key Vault-backed secret wiring (AIW-73) - see docs/operations/dev-environment.md
# for the real health-check-gap history this file's own git blame tells the rest of.
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

data "azurerm_client_config" "current" {}

# AIW-72's real DEV database/role, read from its own state file - not re-derived or duplicated
# here. Read-only: this environment never writes to nonprod's state, only reads its outputs.
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

  name                = "kv-aiw-dev-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  tenant_id           = data.azurerm_client_config.current.tenant_id

  allowed_ip_ranges = ["91.115.38.199"]

  # AIW-81's real finding: GitHub Actions' automatic DEV deployment runs terraform apply from a
  # GitHub-hosted runner with no stable IP and no "trusted service" status - a real deploy hit
  # ForbiddenByFirewall even after the correct RBAC role existed. RBAC (already least-privilege
  # - see this environment's own role assignments) is the real access control here, matching
  # Key Vault's own documented security model; DEV is the one environment where a CI-driven
  # apply actually needs this today.
  network_default_action = "Allow"

  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "azurerm_role_assignment" "terraform_admin_kv_secrets_officer" {
  scope                = module.key_vault.id
  role_definition_name = "Key Vault Secrets Officer"
  # AIW-81's own second real finding: "current caller" is fine as long as exactly one identity
  # ever runs apply, but breaks the moment a second one (CI) does too - Terraform then wants to
  # replace this grant every time the caller differs from whoever last applied, and neither a
  # human nor CI's own Contributor role can perform that delete (role-assignment management is
  # a separate permission from Contributor). A fixed, stable object id - the real human
  # terraform-admin identity this project has used for every manual apply so far - decouples
  # this grant from "whoever happens to be running apply right now", the same fix already
  # applied to CI's own grant above.
  principal_id = "4720b2b3-44ba-400d-8f92-53cd5babde85"
}

# AIW-81: looked up by its stable display name, not a hardcoded object ID, matching
# environments/shared's own pattern - CI now runs terraform apply for real (deploy-dev.yml),
# and Key Vault's data-plane RBAC (reading/writing an actual secret's value) is a separate
# namespace from the Contributor role CI already holds on this resource group. Without this,
# a real deploy (2026-09-12) failed: the "current caller" grant above is tied to whichever
# identity happens to be running apply, so CI running apply forced that role assignment to be
# replaced (destroy the human's grant, create CI's) - but the plan first needs to read the
# three existing azurerm_key_vault_secret resources, which requires an identity that already
# has this role *before* the replacement completes. Granting CI its own, independent role
# assignment (not tied to "whoever is currently authenticated") breaks that chicken-and-egg
# cycle for good, for any future caller.
data "azuread_service_principal" "github_actions" {
  display_name = "aiw-github-actions-terraform"
}

resource "azurerm_role_assignment" "github_actions_kv_secrets_officer" {
  scope                = module.key_vault.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azuread_service_principal.github_actions.object_id
}

resource "azurerm_role_assignment" "backend_kv_secrets_user" {
  scope                = module.key_vault.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.backend.principal_id
}

# The real values AIW-72 generated (random_password resources in nonprod's own state) - never a
# literal value in this file. AC: "No application/API/database secrets are committed to Git or
# baked into images" - these three resources are the one place they're written anywhere at all,
# into Key Vault itself, and even there only after the role assignment above exists.
resource "azurerm_key_vault_secret" "spring_datasource_url" {
  name            = "spring-datasource-url"
  value           = "jdbc:postgresql://${data.terraform_remote_state.nonprod.outputs.postgresql_fqdn}:5432/${data.terraform_remote_state.nonprod.outputs.dev_database_name}?sslmode=require"
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_username" {
  name            = "spring-datasource-username"
  value           = data.terraform_remote_state.nonprod.outputs.dev_app_username
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_password" {
  name            = "spring-datasource-password"
  value           = data.terraform_remote_state.nonprod.outputs.dev_app_password
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-11T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
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

  # AIW-73: this is what actually closes the health-check gap documented since AIW-71 - the
  # real AIW-72 Postgres credentials, resolved from Key Vault at runtime via the managed
  # identity above, never a plain env var value.
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
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }

  depends_on = [azurerm_role_assignment.backend_acr_pull, azurerm_role_assignment.backend_kv_secrets_user]
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
