# Foundation only (AIW-69) - AIW-74 ("Provision Azure STAGING environment") adds the real
# resource instantiations into this same file, reusing the shared NONPROD PostgreSQL server
# from AIW-72 (a separate database on it, per docs/operations/azure-environment-architecture.md's
# documented DEV/STAGING cost-optimization exception - never extended to PROD).
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-staging-swc"
  location = "Sweden Central"
  tags = {
    environment = "staging"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
