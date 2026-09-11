# Scaffolded by AIW-69 (foundation) - AIW-71/AIW-74/AIW-75 instantiate this per environment.
# Environment-specific config (API keys, DB connection strings) is injected as Container App
# secret references sourced from that environment's own Key Vault (AIW-73/secret-management.md's
# decision), never baked into the image itself - this module accepts that via the caller's own
# env var/secret blocks once AIW-71 fills them in, deliberately left minimal here.
resource "azurerm_container_app" "this" {
  name                         = var.name
  resource_group_name          = var.resource_group_name
  container_app_environment_id = var.container_app_environment_id
  revision_mode                = "Single"

  template {
    min_replicas = var.min_replicas
    max_replicas = var.max_replicas

    container {
      name   = "backend"
      image  = var.image
      cpu    = var.cpu
      memory = var.memory
    }
  }

  ingress {
    external_enabled = true
    target_port      = var.target_port
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }

  tags = var.tags
}
