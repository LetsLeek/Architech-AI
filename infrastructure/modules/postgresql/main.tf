# Scaffolded by AIW-69 (foundation) - AIW-72 instantiates this for the shared NONPROD
# (DEV+STAGING) server, AIW-76 instantiates it again for PROD's own dedicated, privately
# connected server with real backup/DR settings, per
# docs/operations/azure-environment-architecture.md's isolation rules.
resource "azurerm_postgresql_flexible_server" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location

  administrator_login    = var.administrator_login
  administrator_password = var.administrator_password

  sku_name   = var.sku_name
  storage_mb = var.storage_mb
  version    = "16"

  backup_retention_days         = var.backup_retention_days
  public_network_access_enabled = var.public_network_access_enabled

  tags = var.tags

  lifecycle {
    # administrator_password: Key-Vault-sourced (AIW-73) and may rotate outside of a Terraform
    # apply - never force a server replacement just because the password variable's value
    # changed between runs.
    # zone: Azure auto-assigns an availability zone for a non-HA Burstable-tier server (a real
    # `plan` against AIW-72's actually-created server showed this as undeclared drift) - which
    # specific zone it picks isn't a decision this project needs to pin, unlike the Container
    # App's own required workload profile (AIW-71), so it's ignored here rather than hardcoded.
    ignore_changes = [administrator_password, zone]
  }
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "this" {
  for_each         = var.firewall_rules
  name             = each.key
  server_id        = azurerm_postgresql_flexible_server.this.id
  start_ip_address = each.value.start_ip_address
  end_ip_address   = each.value.end_ip_address
}
