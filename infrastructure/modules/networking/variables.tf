variable "vnet_name" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "address_space" {
  type        = list(string)
  description = "AIW-76 owns the real addressing plan when private connectivity for PROD Postgres is wired in; this default is a placeholder /16 not yet used by any resource."
  default     = ["10.0.0.0/16"]
}

variable "subnets" {
  type = map(object({
    address_prefixes = list(string)
  }))
  description = "Named subnets to create, e.g. { private-endpoints = { address_prefixes = [\"10.0.1.0/24\"] } } - empty by default until a real consumer (AIW-76) needs one."
  default     = {}
}

variable "tags" {
  type = map(string)
}
