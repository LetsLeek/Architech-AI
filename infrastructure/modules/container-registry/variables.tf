variable "name" {
  type        = string
  description = "ACR name - alphanumeric only, no hyphens (acraiwshared per the naming convention)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type    = string
  default = "Sweden Central"
}

variable "sku" {
  type        = string
  description = "AIW-70 owns the actual SKU decision; Basic is the safe default until real usage is known."
  default     = "Basic"
}

variable "tags" {
  type = map(string)
}
