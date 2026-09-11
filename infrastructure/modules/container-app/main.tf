# Scaffolded by AIW-69 (foundation), filled in for real use by AIW-71 (DEV) - AIW-74/AIW-75
# instantiate this again for STAGING/PROD. Environment-specific *secret* config (API keys, DB
# connection strings) is injected as Container App secret references sourced from that
# environment's own Key Vault (AIW-73/secret-management.md's decision) - not yet wired in here,
# since Key Vault doesn't exist until AIW-73; var.env below is non-secret config only for now.
resource "azurerm_container_app" "this" {
  name                         = var.name
  resource_group_name          = var.resource_group_name
  container_app_environment_id = var.container_app_environment_id
  revision_mode                = "Single"

  # Explicit, matching the container-app-environment module's own required "Consumption"
  # workload profile (same real-drift discovery: Azure assigns this on the Container App itself
  # too, and an undeclared value shows up as Terraform proposing to remove it).
  workload_profile_name = "Consumption"

  # User-assigned managed identity, not a system-assigned one - lets the same identity be
  # created ahead of time (by the caller) and referenced both here and in the ACR AcrPull role
  # assignment, without a create-order dependency between "the identity exists" and "the
  # Container App that needs it exists."
  identity {
    type         = "UserAssigned"
    identity_ids = [var.user_assigned_identity_id]
  }

  # Pulls via the managed identity above, never a registry username/password - matches AIW-70's
  # own "least-privilege Azure identities rather than shared admin credentials" decision.
  registry {
    server   = var.registry_server
    identity = var.user_assigned_identity_id
  }

  template {
    min_replicas = var.min_replicas
    max_replicas = var.max_replicas

    container {
      name   = "backend"
      image  = var.image
      cpu    = var.cpu
      memory = var.memory

      dynamic "env" {
        for_each = var.env
        content {
          name  = env.value.name
          value = env.value.value
        }
      }
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
