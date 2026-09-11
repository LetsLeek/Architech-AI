# Scaffolded by AIW-69 (foundation) - RBAC role assignments (which managed identity may read
# which secret, per docs/operations/azure-environment-architecture.md's "a DEV or STAGING
# credential/identity has no access path to a PROD secret" rule) are AIW-73's own scope, added
# alongside this module's real instantiation per environment.
resource "azurerm_key_vault" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  tenant_id           = var.tenant_id
  sku_name            = var.sku_name

  # RBAC, not the legacy access-policy model - every grant is a real, auditable role
  # assignment (AIW-73), never an implicit vault-level policy list.
  rbac_authorization_enabled = true

  # Recoverable by default - an accidental delete of a secret/vault is not immediately
  # unrecoverable, matching this project's own "prefer reversible over destructive" discipline.
  soft_delete_retention_days = 90
  purge_protection_enabled   = true

  # Default-deny network ACL - only Azure services (Container Apps' own secret-reference
  # mechanism included) bypass it by default; var.allowed_ip_ranges lets AIW-73 add specific
  # trusted ranges (e.g. an office/VPN egress IP) once a real one is known, empty until then.
  network_acls {
    default_action = "Deny"
    bypass         = "AzureServices"
    ip_rules       = var.allowed_ip_ranges
  }

  tags = var.tags
}
