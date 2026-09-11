# Foundation only (AIW-69) - holds the resource group real shared resources (e.g. AIW-70's ACR)
# get created into. This file grows as those tickets land; it does not instantiate the
# container-registry/monitoring/etc. modules itself yet.
module "resource_group" {
  source = "../../modules/resource-group"

  name     = "rg-aiw-shared-swc"
  location = "Sweden Central"
  tags = {
    environment = "shared"
    workload    = "aiw"
    managed-by  = "terraform"
  }
}
