# AIW-72: one PostgreSQL Flexible Server, shared by DEV and STAGING only (never PROD, which
# gets its own dedicated, privately-connected server in AIW-76) - the documented cost-
# optimization exception in docs/operations/azure-environment-architecture.md. Two separate
# databases, two separate least-privilege roles (never a shared app-level credential between
# DEV and STAGING) - only the server-level admin credential (used solely by this Terraform
# config itself to provision the databases/roles, never handed to either application) is
# actually shared.
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-nonprod-swc"
  location = "Sweden Central"
  tags = {
    environment = "nonprod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "random_password" "postgresql_admin" {
  length  = 32
  special = true
  # Postgres connection strings/URLs choke on some special characters (":", "/", "@") even when
  # properly escaped in some client libraries - restricting to a safe punctuation set avoids
  # that class of problem entirely rather than fixing it after hitting it.
  override_special = "!#%^*()-_=+"
}

resource "random_password" "dev_app" {
  length           = 32
  special          = true
  override_special = "!#%^*()-_=+"
}

resource "random_password" "staging_app" {
  length           = 32
  special          = true
  override_special = "!#%^*()-_=+"
}

module "postgresql" {
  source = "../../modules/postgresql"

  name                = "psql-aiw-nonprod-swc"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location

  administrator_login    = "aiwadmin"
  administrator_password = random_password.postgresql_admin.result

  # AIW-72 owns the real NONPROD sizing decision (not AIW-69's placeholder default, restated
  # here explicitly so it's a deliberate choice, not an inherited accident): smallest real
  # Burstable tier, matching this project's own "start at the honest, cost-conscious baseline"
  # posture (the same one applied to coverage/quality gate thresholds).
  sku_name   = "B_Standard_B1ms"
  storage_mb = 32768

  # Real deny-all-by-default Azure behavior: nothing reaches this server without an explicit
  # rule. "azure-services" (the documented 0.0.0.0/0.0.0.0 special case) is what lets the DEV
  # Container App actually connect once AIW-73 wires the connection string in; "terraform-admin"
  # is whoever's running this apply (this session's own current public IP, verified via
  # api.ipify.org) - needed only to create the two databases/roles below, since the
  # `postgresql` Terraform provider connects for real, not through the Azure control plane.
  # Revisit "terraform-admin" if this ever runs from CI instead of a human's own machine.
  firewall_rules = {
    azure-services = {
      start_ip_address = "0.0.0.0"
      end_ip_address   = "0.0.0.0"
    }
    terraform-admin = {
      start_ip_address = "91.115.38.199"
      end_ip_address   = "91.115.38.199"
    }
  }

  tags = {
    environment = "nonprod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}

resource "postgresql_database" "dev" {
  name       = "aiw_dev"
  owner      = postgresql_role.dev_app.name
  depends_on = [module.postgresql]
}

resource "postgresql_database" "staging" {
  name       = "aiw_staging"
  owner      = postgresql_role.staging_app.name
  depends_on = [module.postgresql]
}

# Real finding, caught by actually testing the "least-privilege" claim below rather than
# trusting database ownership alone: PostgreSQL grants CONNECT on every database to the PUBLIC
# pseudo-role by default, regardless of ownership - a real psql connection as aiw_dev_app to
# aiw_staging succeeded before this grant existed. Explicitly revoking PUBLIC's privileges
# (privileges = []) on each database is what actually makes the "DEV credential has no path to
# STAGING's data" claim true, not just the ownership assignment above.
resource "postgresql_grant" "dev_revoke_public" {
  database    = postgresql_database.dev.name
  role        = "public"
  object_type = "database"
  privileges  = []
}

resource "postgresql_grant" "staging_revoke_public" {
  database    = postgresql_database.staging.name
  role        = "public"
  object_type = "database"
  privileges  = []
}

# Another real finding from actually testing, not assumed: PostgreSQL 15+ no longer gives a
# database's nominal owner implicit CREATE on that database's own `public` schema (the schema
# itself has its own separate owner/ACL) - a real `CREATE TABLE` as aiw_dev_app on aiw_dev
# failed with "permission denied for schema public" before this grant existed. Without this,
# Flyway's very first migration (creating its own schema_history table) would fail identically.
resource "postgresql_grant" "dev_schema" {
  database    = postgresql_database.dev.name
  role        = postgresql_role.dev_app.name
  schema      = "public"
  object_type = "schema"
  privileges  = ["CREATE", "USAGE"]
}

resource "postgresql_grant" "staging_schema" {
  database    = postgresql_database.staging.name
  role        = postgresql_role.staging_app.name
  schema      = "public"
  object_type = "schema"
  privileges  = ["CREATE", "USAGE"]
}

# Least-privilege: each role can log in, but owns (and therefore only has meaningful access to)
# exactly one database - the DEV app credential has no path to STAGING's data or vice versa,
# structurally, not by convention. Neither role is a superuser.
resource "postgresql_role" "dev_app" {
  name       = "aiw_dev_app"
  login      = true
  password   = random_password.dev_app.result
  depends_on = [module.postgresql]
}

resource "postgresql_role" "staging_app" {
  name       = "aiw_staging_app"
  login      = true
  password   = random_password.staging_app.result
  depends_on = [module.postgresql]
}
