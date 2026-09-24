output "resource_group_name" {
  value = azurerm_resource_group.tfstate.name
}

output "storage_account_name" {
  value = azurerm_storage_account.tfstate.name
}

output "container_name" {
  value = azurerm_storage_container.tfstate.name
}

output "github_actions_client_id" {
  value       = azuread_application.github_actions.client_id
  description = "Set as the AZURE_CLIENT_ID GitHub Actions repository variable (not a secret - OIDC needs no client secret)."
}

output "azure_tenant_id" {
  value       = data.azurerm_client_config.current.tenant_id
  description = "Set as the AZURE_TENANT_ID GitHub Actions repository variable."
}

output "azure_subscription_id" {
  value       = data.azurerm_client_config.current.subscription_id
  description = "Set as the AZURE_SUBSCRIPTION_ID GitHub Actions repository variable."
}
