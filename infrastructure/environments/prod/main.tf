# Foundation only (AIW-69) - AIW-75 ("Provision isolated Azure PRODUCTION environment") and
# AIW-76 (dedicated PROD PostgreSQL with private connectivity/backups) add the real resource
# instantiations here. This resource group is, by construction, never shared with DEV/STAGING -
# see docs/operations/azure-environment-architecture.md's isolation rules.
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-prod-swc"
  location = "Sweden Central"
  tags = {
    environment = "prod"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
