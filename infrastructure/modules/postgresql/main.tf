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
    # The admin password is Key-Vault-sourced (AIW-73) and may rotate outside of a Terraform
    # apply - never force a server replacement just because the password variable's value
    # changed between runs.
    ignore_changes = [administrator_password]
  }
}
