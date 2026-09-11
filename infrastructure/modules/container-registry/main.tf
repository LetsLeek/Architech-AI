# Scaffolded by AIW-69 (foundation) - actually instantiated and tuned by AIW-70.
resource "azurerm_container_registry" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku
  # Admin user deliberately left disabled - pulls/pushes authenticate via managed identity/RBAC
  # (AIW-73), never a shared admin credential.
  admin_enabled = false
  tags          = var.tags
}
