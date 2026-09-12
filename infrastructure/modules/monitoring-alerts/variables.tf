variable "name_prefix" {
  type        = string
  description = "e.g. \"aiw-prod\" - used to name every alert resource (alert-<name_prefix>-<signal>)."
}

variable "short_name" {
  type        = string
  description = "Action group short name - Azure requires <= 12 characters."
  validation {
    condition     = length(var.short_name) <= 12
    error_message = "short_name must be 12 characters or fewer (Azure Monitor action group constraint)."
  }
}

variable "resource_group_name" {
  type = string
}

variable "container_app_id" {
  type        = string
  description = "Resource id of the Container App these alerts monitor - CPU/memory/restart/HTTP-error/latency signals all scope to this one resource."
}

variable "postgresql_server_id" {
  type        = string
  description = "Resource id of the PostgreSQL Flexible Server these alerts monitor - availability/storage signals scope to this one resource."
}

variable "email_receivers" {
  type = list(object({
    name          = string
    email_address = string
  }))
  description = "Who the action group notifies - at least one real recipient, never a placeholder."
}

variable "tags" {
  type = map(string)
}
