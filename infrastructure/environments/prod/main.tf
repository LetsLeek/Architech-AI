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

resource "random_password" "postgresql_admin" {
  length  = 32
  special = true
  # Postgres connection strings/URLs choke on some special characters (":", "/", "@") even when
  # properly escaped in some client libraries - restricting to a safe punctuation set avoids
  # that class of problem entirely rather than fixing it after hitting it (same as NONPROD).
  override_special = "!#%^*()-_=+"
}

resource "random_password" "prod_app" {
  length           = 32
  special          = true
  override_special = "!#%^*()-_=+"
}

# AIW-76: PROD's own dedicated PostgreSQL Flexible Server - never the shared NONPROD server DEV/
# STAGING use (AIW-72's own documented "never extended to PROD" exception). Satisfies "PROD
# PostgreSQL is provisioned separately from NONPROD" by construction: a different server, in
# PROD's own resource group, with its own admin credential never shared with any other
# environment's Terraform state.
module "postgresql" {
  source = "../../modules/postgresql"

  name                = "psql-aiw-prod-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location

  administrator_login    = "aiwadmin"
  administrator_password = random_password.postgresql_admin.result

  # Same minimal Burstable tier as NONPROD to start - this platform has no real production
  # traffic yet, so paying for more compute than the workload needs would be premature; trivial
  # to resize later (Flexible Server SKU changes are an in-place operation, not a migration).
  sku_name   = "B_Standard_B1ms"
  storage_mb = 32768

  # PROD-specific backup/DR uplift over NONPROD (AC: "Automatic backups and point-in-time
  # recovery settings are configured"): Azure Postgres Flexible Server takes continuous
  # transaction-log backups automatically within the retention window (point-in-time recovery
  # needs no separate toggle beyond retention_days) - 35 days is the real Azure maximum, chosen
  # deliberately for production data rather than NONPROD's 7-day default.
  backup_retention_days        = 35
  geo_redundant_backup_enabled = true

  # AC: "not exposed broadly to public internet; private connectivity is used where practical
  # for the selected runtime architecture." Real, honest trade-off (documented in full in
  # docs/operations/prod-environment.md): this platform's Container Apps Environment is
  # Consumption-only, with no VNet integration anywhere yet (a genuinely bigger change, and one
  # that would still be blocked today by AIW-74's own real Container Apps Environment quota
  # limit) - a true Private Endpoint isn't practical for the current runtime architecture. The
  # honest interim posture is the same deny-all-plus-explicit-allowlist NONPROD already uses,
  # never a broad public range: "azure-services" (the documented 0.0.0.0/0.0.0.0 special case,
  # needed for the eventual PROD Container App to connect) and "terraform-admin" (this apply's
  # own current caller, needed only because the `postgresql` provider connects directly, not
  # through the Azure control plane).
  firewall_rules = {
    azure-services = {
      start_ip_address = "0.0.0.0"
      end_ip_address   = "0.0.0.0"
    }
    terraform-admin = {
      start_ip_address = "91.115.38.199"
      end_ip_address   = "91.115.38.199"
    }
  }

  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "postgresql_database" "prod" {
  name       = "aiw_prod"
  owner      = postgresql_role.prod_app.name
  depends_on = [module.postgresql]
}

# Same two real findings AIW-72 discovered via live psql testing, applied here from the start
# rather than re-discovered: PUBLIC has implicit CONNECT on every database regardless of
# ownership, and PG15+ no longer gives an owner implicit CREATE on its own public schema.
resource "postgresql_grant" "prod_revoke_public" {
  database    = postgresql_database.prod.name
  role        = "public"
  object_type = "database"
  privileges  = []
}

resource "postgresql_grant" "prod_schema" {
  database    = postgresql_database.prod.name
  role        = postgresql_role.prod_app.name
  schema      = "public"
  object_type = "schema"
  privileges  = ["CREATE", "USAGE"]
}

# Least-privilege, dedicated PROD credential (AC: "Production application uses dedicated
# credentials and aiw_prod database") - not a superuser, not shared with any other environment.
resource "postgresql_role" "prod_app" {
  name       = "aiw_prod_app"
  login      = true
  password   = random_password.prod_app.result
  depends_on = [module.postgresql]
}

# The real values generated above - never a literal value in this file (AC: no application/
# database secrets committed to Git or baked into images, same as AIW-73/DEV).
resource "azurerm_key_vault_secret" "spring_datasource_url" {
  name            = "spring-datasource-url"
  value           = "jdbc:postgresql://${module.postgresql.fqdn}:5432/${postgresql_database.prod.name}?sslmode=require"
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-12T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_username" {
  name            = "spring-datasource-username"
  value           = postgresql_role.prod_app.name
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-12T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

resource "azurerm_key_vault_secret" "spring_datasource_password" {
  name            = "spring-datasource-password"
  value           = random_password.prod_app.result
  content_type    = "text/plain"
  key_vault_id    = module.key_vault.id
  expiration_date = "2027-09-12T00:00:00Z"
  depends_on      = [azurerm_role_assignment.terraform_admin_kv_secrets_officer]
}

# NOT YET APPLIED (AIW-75): depends on the Container Apps Environment above, still pinned on
# the same real subscription quota blocker - see docs/operations/prod-environment.md. The
# key_vault_secrets/secret_env wiring below is real, promotion-ready configuration (AIW-76),
# ready to take effect the moment the Container App itself can be created.
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
