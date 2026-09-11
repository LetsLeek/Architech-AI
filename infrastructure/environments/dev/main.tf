# Foundation only (AIW-69) - AIW-71 ("Provision Azure DEV environment") adds the
# container-app-environment/container-app/monitoring module instantiations into this same file;
# AIW-72/AIW-73 add the shared NONPROD PostgreSQL and Key Vault wiring.
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-dev-swc"
  location = "Sweden Central"
  tags = {
    environment = "dev"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
