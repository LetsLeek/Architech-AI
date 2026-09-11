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

variable "tags" {
  type = map(string)
}
