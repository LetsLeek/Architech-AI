variable "name" {
  type        = string
  description = "Container Apps Environment name (cae-aiw-<scope>-<region>)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "log_analytics_workspace_id" {
  type        = string
  description = "ARM resource id (monitoring module's id output) of the Log Analytics workspace this environment's logs flow to."
}

variable "tags" {
  type = map(string)
}
