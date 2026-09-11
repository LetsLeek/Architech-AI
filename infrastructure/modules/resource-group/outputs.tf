output "id" {
  value       = azurerm_resource_group.this.id
  description = "Resource group resource ID - pass to other modules that must scope resources into this group."
}

output "name" {
  value = azurerm_resource_group.this.name
}

output "location" {
  value = azurerm_resource_group.this.location
}
