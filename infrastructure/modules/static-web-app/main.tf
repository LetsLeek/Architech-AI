# Scaffolded for real use by AIW-71 (DEV) - AIW-74/AIW-75 instantiate this again for
# STAGING/PROD. Deployment content itself (the built frontend/dist output) is pushed by Azure's
# own GitHub Actions integration (the deployment_token output below), not by this Terraform
# resource - this resource only provisions the hosting target.
resource "azurerm_static_web_app" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku_tier            = var.sku_tier
  sku_size            = var.sku_tier

  tags = var.tags
}
