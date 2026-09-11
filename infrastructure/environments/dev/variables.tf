variable "backend_image_tag" {
  type        = string
  description = "Git commit SHA tag of the architech-backend image in ACR to deploy - never 'latest' (AIW-70's own tagging policy). Passed via -var at apply time, not hardcoded, so a redeploy to a newer SHA is a one-line command, not a code change."
}
