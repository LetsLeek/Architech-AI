variable "name" {
  type        = string
  description = "Static Web App name (swa-aiw-<scope>-<region>)."
}

variable "resource_group_name" {
  type = string
}

variable "location" {
  type = string
  # Static Web Apps are only available in a fixed subset of regions (verified against the real
  # Microsoft.Web resource-provider location list, not guessed): Central US, East US 2, West US
  # 2, West Europe, East Asia. Sweden Central isn't one of them. West Europe - the obvious EU
  # choice - was tried first and rejected for real with the same "region is currently not
  # accepting new customers" error AIW-69 already hit for Sweden Central; East Asia and Central
  # US were both then verified to actually work (real create+delete test), and Central US was
  # chosen as generally lower-latency from Europe than East Asia. The platform's own naming
  # convention still uses `swc` in the resource *name* for consistency with every other
  # DEV/STAGING/PROD resource, even though this one resource's actual `location` differs -
  # documented here and in docs/operations/azure-environment-architecture.md rather than
  # silently inconsistent.
  default = "Central US"
}

variable "sku_tier" {
  type        = string
  description = "Free tier is a genuinely free, real Azure SKU (not a trial) - sufficient for a DEV-shaped SPA; STAGING/PROD (AIW-74/75) revisit if the Free tier's request/bandwidth limits are ever actually hit."
  default     = "Free"
}

variable "app_location" {
  type        = string
  description = "Path to the built frontend output, relative to the repo root, for the GitHub Actions deployment workflow Azure generates."
  default     = "frontend/dist"
}

variable "tags" {
  type = map(string)
}
