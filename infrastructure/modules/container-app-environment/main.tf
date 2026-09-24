# Scaffolded by AIW-69 (foundation) - AIW-71/AIW-74/AIW-75 each instantiate one of these per
# environment, wiring it to that environment's own monitoring module output (never a shared
# Log Analytics workspace across environments, per the architecture doc's isolation rules).
resource "azurerm_container_app_environment" "this" {
  name                       = var.name
  resource_group_name        = var.resource_group_name
  location                   = var.location
  log_analytics_workspace_id = var.log_analytics_workspace_id
  tags                       = var.tags

  # Explicit, not left to drift: Azure auto-creates a default "Consumption" workload profile on
  # every environment of this kind whether or not it's declared - a real `terraform plan` against
  # the actually-created AIW-71 environment showed Terraform wanting to *remove* it as
  # undeclared drift. Declaring it here, matching exactly what Azure itself returns, keeps
  # `plan` clean instead of proposing to delete infrastructure the environment actually needs.
  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}
