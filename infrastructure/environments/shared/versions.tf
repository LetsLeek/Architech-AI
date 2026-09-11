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

  # Empty on purpose - real values come from `terraform init -backend-config=...` (see
  # infrastructure/README.md), sourced from infrastructure/bootstrap's own outputs. Keeping this
  # block empty (rather than hardcoding the storage account name here) is what lets the exact
  # same file be used unmodified if the state storage account is ever recreated.
  backend "azurerm" {
    use_azuread_auth = true
  }
}

provider "azurerm" {
  features {}
}

provider "azuread" {}
