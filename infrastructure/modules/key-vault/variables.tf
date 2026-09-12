variable "name" {
  type        = string
  description = "Key Vault name (kv-aiw-<scope>-<region>)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "tenant_id" {
  type        = string
  description = "Azure AD tenant ID this vault trusts for RBAC-based access."
}

variable "sku_name" {
  type    = string
  default = "standard"
}

variable "allowed_ip_ranges" {
  type        = list(string)
  description = "IP ranges allowed through the default-deny network ACL, beyond trusted Azure services - empty until AIW-73 wires in a real trusted range (e.g. office/VPN egress)."
  default     = []
}

variable "network_default_action" {
  type        = string
  description = <<-EOT
    "Deny" (the secure default) or "Allow". AIW-81's own real finding: GitHub Actions runners
    have no stable IP range worth allowlisting and are not a Key Vault "trusted service" (the
    real error was "Client address is not authorized and caller is not a trusted service" /
    ForbiddenByRbac's own network-layer sibling, ForbiddenByFirewall) - an environment whose
    Key Vault a CI-driven deploy must read secrets from needs "Allow" here, relying on RBAC
    (already least-privilege - see the module's own role assignments) as the real access
    control, which is Key Vault's own documented security model regardless of network ACLs.
  EOT
  default     = "Deny"
  validation {
    condition     = contains(["Allow", "Deny"], var.network_default_action)
    error_message = "network_default_action must be \"Allow\" or \"Deny\"."
  }
}

variable "tags" {
  type = map(string)
}
