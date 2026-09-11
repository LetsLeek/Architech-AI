variable "name" {
  type        = string
  description = "Log Analytics workspace name (log-aiw-<scope>-<region>)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "retention_in_days" {
  type        = number
  description = "AIW-77 owns the real retention/alerting policy; 30 days is a safe placeholder default."
  default     = 30
}

variable "tags" {
  type = map(string)
}
