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

variable "geo_redundant_backup_enabled" {
  type        = bool
  description = "AIW-76 enables this for PROD's real point-in-time-recovery/DR posture; NONPROD stays false (not worth the cost for DEV/STAGING data)."
  default     = false
}

variable "public_network_access_enabled" {
  type        = bool
  description = "AIW-76 disables this for PROD in favor of private connectivity; non-prod may stay enabled behind firewall rules."
  default     = true
}

variable "firewall_rules" {
  type = map(object({
    start_ip_address = string
    end_ip_address   = string
  }))
  description = <<-EOT
    Named firewall rules (only meaningful while public_network_access_enabled is true) - deny-all
    is the real Azure default, so nothing (not even Azure Container Apps) can reach this server
    without an explicit rule here. Use "0.0.0.0"/"0.0.0.0" for Azure's own documented
    allow-all-Azure-services special case, not a real public IP range.
  EOT
  default     = {}
}

variable "tags" {
  type = map(string)
}
