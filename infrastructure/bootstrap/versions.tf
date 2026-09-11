terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
  }

  # Deliberately local state - the one, explicitly documented exception in this whole
  # infrastructure tree (AIW-69's own acceptance criteria: "remote state uses an Azure Storage
  # backend OR an explicitly documented bootstrap path"). This config creates the very storage
  # account every other environment's remote state depends on, so it cannot depend on that
  # backend existing yet. See README.md for the one-time run procedure; the resulting local
  # state file is gitignored, never committed - re-run only if the bootstrap resources are lost.
}

provider "azurerm" {
  features {}
  # Required because the state storage account has shared_access_key_enabled = false: without
  # this, the provider's own post-create data-plane readiness check polls via account key and
  # fails with "Key based authentication is not permitted on this storage account" - a real
  # error hit while applying this config, not a hypothetical. This makes the provider's own
  # storage data-plane calls use the caller's Azure AD identity instead, consistent with how the
  # azurerm backend itself is configured (use_azuread_auth = true) in every environment root.
  storage_use_azuread = true
}

provider "azuread" {}
