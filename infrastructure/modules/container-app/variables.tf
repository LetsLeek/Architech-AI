variable "name" {
  type        = string
  description = "Container App name (ca-aiw-backend-<scope>)."
}

variable "resource_group_name" {
  type = string
}

variable "container_app_environment_id" {
  type = string
}

variable "image" {
  type        = string
  description = "Full ACR image reference including tag/digest - the same image is promoted DEV -> STAGING -> PROD unchanged (AIW-68's own contract), only this value's tag and the env vars below differ per environment."
}

variable "cpu" {
  type    = number
  default = 0.5
}

variable "memory" {
  type    = string
  default = "1Gi"
}

variable "min_replicas" {
  type    = number
  default = 0
}

variable "max_replicas" {
  type    = number
  default = 1
}

variable "target_port" {
  type    = number
  default = 8080
}

variable "user_assigned_identity_id" {
  type        = string
  description = "Resource ID of the user-assigned managed identity this app pulls its image and (eventually, AIW-73) reads Key Vault secrets as."
}

variable "registry_server" {
  type        = string
  description = "ACR login server (e.g. acraiwshared.azurecr.io) the image above is pulled from."
}

variable "env" {
  type = list(object({
    name  = string
    value = string
  }))
  description = "Non-secret environment variables only - secret-backed config (AIW-73) is a separate mechanism (Key Vault secret references), not this variable."
  default     = []
}

variable "tags" {
  type = map(string)
}
