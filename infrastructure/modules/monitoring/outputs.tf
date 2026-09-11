output "id" {
  value = azurerm_log_analytics_workspace.this.id
}

output "workspace_id" {
  value       = azurerm_log_analytics_workspace.this.workspace_id
  description = "The workspace's own GUID (distinct from the ARM resource id) - Container Apps Environment needs this."
}

output "primary_shared_key" {
  value     = azurerm_log_analytics_workspace.this.primary_shared_key
  sensitive = true
}
