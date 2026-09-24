# Scaffolded by AIW-69 (foundation) - the workspace itself is needed early (Container Apps
# Environments require one), but alert rules/action groups/diagnostic settings are AIW-77's own
# scope, added here later without changing this module's basic shape.
resource "azurerm_log_analytics_workspace" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = var.retention_in_days
  tags                = var.tags
}
