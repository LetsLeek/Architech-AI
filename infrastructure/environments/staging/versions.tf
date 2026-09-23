terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    # AIW-185: looks up the CI service principal by display name for its own Key Vault
    # data-plane grant below, same as environments/dev already does.
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    # AIW-185: generates the backend's shared API-key gate value.
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "azurerm" {
    use_azuread_auth = true
  }
}

provider "azurerm" {
  features {}
}

provider "azuread" {}
