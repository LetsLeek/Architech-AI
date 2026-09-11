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

  lifecycle {
    # Real drift discovery (AIW-73): the frontend-deploy-*.yml workflows' own Azure/
    # static-web-apps-deploy action associates a repository/branch with this resource as a
    # side effect of a real deployment - undeclared here, a real `plan` wanted to *remove* that
    # association (the same "Azure sets it, Terraform doesn't own it, don't fight over it"
    # pattern as the Container App workload profile and the Postgres server's availability
    # zone). This project's CI deploy workflow is what should own this value, not a Terraform
    # apply that predates any real deployment.
    ignore_changes = [repository_url, repository_branch]
  }
}
