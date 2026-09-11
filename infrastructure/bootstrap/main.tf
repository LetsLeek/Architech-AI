locals {
  tags = {
    environment = "shared"
    workload    = "aiw"
    managed-by  = "terraform"
    purpose     = "tfstate-bootstrap"
  }
}

# A dedicated resource group, deliberately never `rg-aiw-shared-swc` (that one holds actual
# shared *application* resources, e.g. the ACR from AIW-70) - keeping state storage in its own
# group means the "real" environments/shared config never has to import or co-manage it.
resource "azurerm_resource_group" "tfstate" {
  name     = "rg-aiw-tfstate-swc"
  location = "Sweden Central"
  tags     = local.tags
}

# Queue logging IS configured below, via azurerm_storage_account_queue_properties.tfstate - the
# separate resource the provider's own v4 deprecation notice recommends over this resource's
# inline queue_properties block (removed entirely in provider v5). This rule predates that split
# and doesn't recognize the newer resource as satisfying it; suppressed on the line below with
# the reason recorded here, not silently ignored.
resource "azurerm_storage_account" "tfstate" { # nosemgrep: terraform.azure.security.storage.storage-queue-services-logging.storage-queue-services-logging
  name                = "staiwtfstateswc"
  resource_group_name = azurerm_resource_group.tfstate.name
  location            = azurerm_resource_group.tfstate.location

  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  # Access is via Azure AD/RBAC (Storage Blob Data Contributor) only - no account key ever
  # leaves this subscription, so no key-based secret needs to exist in CI or anywhere else.
  shared_access_key_enabled = false

  blob_properties {
    versioning_enabled = true
    delete_retention_policy {
      days = 30
    }
  }

  tags = local.tags
}

# Queue Storage Analytics logging - this account never actually uses queues (state is blob
# only), but Semgrep's terraform-azure ruleset still flags an azurerm_storage_account without
# it; enabling costs nothing on an account with no queue traffic and closes the finding
# honestly rather than suppressing it. A separate resource, not an inline `queue_properties`
# block, per the provider's own v4 deprecation notice ahead of v5's removal of that block.
resource "azurerm_storage_account_queue_properties" "tfstate" {
  storage_account_id = azurerm_storage_account.tfstate.id

  logging {
    delete                = true
    read                  = true
    write                 = true
    version               = "1.0"
    retention_policy_days = 30
  }
}

resource "azurerm_storage_container" "tfstate" {
  name                  = "tfstate"
  storage_account_id    = azurerm_storage_account.tfstate.id
  container_access_type = "private"
}
