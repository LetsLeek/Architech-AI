# Scaffolded by AIW-69 (foundation) - AIW-71/AIW-74/AIW-75 each instantiate one of these per
# environment, wiring it to that environment's own monitoring module output (never a shared
# Log Analytics workspace across environments, per the architecture doc's isolation rules).
resource "azurerm_container_app_environment" "this" {
  name                       = var.name
  resource_group_name        = var.resource_group_name
  location                   = var.location
  log_analytics_workspace_id = var.log_analytics_workspace_id
  tags                       = var.tags
}
