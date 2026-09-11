output "id" {
  value = azurerm_static_web_app.this.id
}

output "default_host_name" {
  value       = azurerm_static_web_app.this.default_host_name
  description = "The real, publicly reachable DEV URL, e.g. https://<default_host_name>."
}

output "api_key" {
  value       = azurerm_static_web_app.this.api_key
  sensitive   = true
  description = "Deployment token for Azure's own Static Web Apps GitHub Actions integration - not a general-purpose secret, scoped only to publishing content to this one Static Web App."
}
