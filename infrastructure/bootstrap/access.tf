data "azurerm_client_config" "current" {}

# The interactive human identity (whoever ran `az login` to apply this bootstrap) needs write
# access to the state container to run any later `terraform init`/`plan`/`apply` against the
# real environments - RBAC role assignment, not a storage account key (shared_access_key_enabled
# is false on the storage account itself).
resource "azurerm_role_assignment" "current_user_tfstate_access" {
  scope                = azurerm_storage_account.tfstate.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = data.azurerm_client_config.current.object_id
}

# CI identity for `terraform fmt`/`validate`/`plan` on pull requests, per AIW-69's own
# acceptance criterion that this must work "without committed secrets" - OpenID Connect
# federated credentials, no client secret ever generated or stored in GitHub.
resource "azuread_application" "github_actions" {
  display_name = "aiw-github-actions-terraform"
}

resource "azuread_service_principal" "github_actions" {
  client_id = azuread_application.github_actions.client_id
}

# Scoped to this exact repository - pull requests targeting develop (where "terraform plan"
# actually needs to run) and pushes to develop (in case a future workflow needs write access
# for `apply`, not exercised by AIW-69's own CI scope, which is read-only fmt/validate/plan).
#
# Subject uses GitHub's owner@id/repo@id form, not the plain owner/repo slug - a real PR run
# against this exact workflow (AIW-69/PR #86) failed AADSTS700213 with the plain-slug subject
# configured first; the actual token GitHub issued carried
# "repo:LetsLeek@89669556/Architech-AI@1360201571:pull_request" instead. These numeric IDs are
# GitHub's own immutable account/repo IDs (stable across a future rename, unlike the slug form),
# confirmed by the real failed run's own log output, not guessed from documentation.
resource "azuread_application_federated_identity_credential" "pull_request" {
  application_id = azuread_application.github_actions.id
  display_name   = "github-pull-request"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = "repo:LetsLeek@89669556/Architech-AI@1360201571:pull_request"
}

resource "azuread_application_federated_identity_credential" "develop_branch" {
  application_id = azuread_application.github_actions.id
  display_name   = "github-develop-branch"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = "repo:LetsLeek@89669556/Architech-AI@1360201571:ref:refs/heads/develop"
}

# Reader is enough for `plan` to read existing resource state at the subscription scope -
# terraform-ci.yml's own plan step stays deliberately Reader-only/read-closed regardless of the
# scoped Contributor grant below, per its own comment.
resource "azurerm_role_assignment" "github_actions_reader" {
  scope                = "/subscriptions/${data.azurerm_client_config.current.subscription_id}"
  role_definition_name = "Reader"
  principal_id         = azuread_service_principal.github_actions.object_id
}

resource "azurerm_role_assignment" "github_actions_tfstate_access" {
  scope                = azurerm_storage_account.tfstate.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azuread_service_principal.github_actions.object_id
}

# AIW-81: the one deliberate exception to "real applies stay human-run/human-approved" -
# automatic DEV deployment on push to develop needs this identity to actually write, not just
# read. Scoped to DEV's own resource group only (never subscription-wide, never
# STAGING/PROD/NONPROD/shared) - least privilege even for the one environment automated
# deployment is allowed to touch. The resource group name is a hardcoded string, not a
# cross-root remote-state reference, matching this project's existing "look it up by its own
# stable name" pattern (e.g. environments/dev's own data.azurerm_container_registry.shared).
resource "azurerm_role_assignment" "github_actions_dev_deploy" {
  scope                = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/rg-aiw-dev-swc"
  role_definition_name = "Contributor"
  principal_id         = azuread_service_principal.github_actions.object_id
}
