variable "name" {
  type        = string
  description = "PostgreSQL Flexible Server name (psql-aiw-<scope>-<region>)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "administrator_login" {
  type = string
}

variable "administrator_password" {
  type        = string
  sensitive   = true
  description = "AIW-73 wires this from Key Vault - never a literal value in any environment .tf file or tfvars committed to git."
}

variable "sku_name" {
  type        = string
  description = "AIW-72/AIW-76 own the real per-environment sizing; B1ms is a minimal non-prod placeholder default."
  default     = "B_Standard_B1ms"
}

variable "storage_mb" {
  type    = number
  default = 32768
}

variable "backup_retention_days" {
  type        = number
  description = "AIW-76 owns PROD's real retention/geo-redundancy requirements."
  default     = 7
}

variable "public_network_access_enabled" {
  type        = bool
  description = "AIW-76 disables this for PROD in favor of private connectivity; non-prod may stay enabled behind firewall rules."
  default     = true
}

variable "tags" {
  type = map(string)
}
