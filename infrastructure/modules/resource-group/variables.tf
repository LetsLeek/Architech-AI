variable "name" {
  type        = string
  description = "Resource group name - follow the rg-aiw-<scope>-<region> convention from docs/operations/azure-environment-architecture.md."
}

variable "location" {
  type        = string
  description = "Azure region (short code in the name, e.g. Sweden Central for swc)."
  default     = "Sweden Central"
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the resource group - at minimum environment, workload, managed-by per the naming/tagging convention."
}
