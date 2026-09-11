terraform {
  required_version = ">= 1.9.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
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

# Connects directly to the real server this same apply creates (module.postgresql.fqdn/
# administrator_login/administrator_password) - a single-root "create the server, then
# configure databases/roles on it" pattern, not cross-module provider passing. Requires the
# server's own firewall to actually allow whoever runs this apply; see main.tf's
# firewall_rules for the real, current allowlist.
provider "postgresql" {
  host = module.postgresql.fqdn
  port = 5432
  # Flexible Server uses a plain username - unlike the now-retired Single Server tier, no
  # "user@servername" suffix is needed or accepted.
  username        = module.postgresql.administrator_login
  password        = random_password.postgresql_admin.result
  sslmode         = "require"
  superuser       = false
  connect_timeout = 30
}
