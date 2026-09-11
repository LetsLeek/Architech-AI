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

variable "tags" {
  type = map(string)
}
