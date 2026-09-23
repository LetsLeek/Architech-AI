terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    # AIW-185: looks up the CI service principal by display name for its own Key Vault
    # data-plane grant, same as environments/dev and environments/staging already do.
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    postgresql = {
      source  = "cyrilgdn/postgresql"
      version = "~> 1.24"
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

# Connects directly to the real server this same apply creates (module.postgresql.fqdn/
# administrator_login/administrator_password) - same single-root "create the server, then
# configure databases/roles on it" pattern as environments/nonprod. Requires the server's own
# firewall to actually allow whoever runs this apply; see main.tf's firewall_rules for the
# real, current allowlist (and docs/operations/prod-environment.md for why this is the honest
# interim posture rather than a private endpoint).
provider "postgresql" {
  host = module.postgresql.fqdn
  port = 5432
  # Flexible Server uses a plain username - no "user@servername" suffix.
  username        = module.postgresql.administrator_login
  password        = random_password.postgresql_admin.result
  sslmode         = "require"
  superuser       = false
  connect_timeout = 30
}
