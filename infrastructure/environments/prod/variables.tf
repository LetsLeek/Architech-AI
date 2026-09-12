variable "backend_image_tag" {
  type        = string
  description = "Git commit SHA tag of the architech-backend image in ACR to deploy - never 'latest' (AIW-70's own tagging policy). Pass -var explicitly for a real promotion; the default below is only there so terraform-ci.yml's read-only `plan` (which never applies) doesn't need this wired in as a CI variable for a value it never acts on. The Container App this tag feeds into is not yet actually created (AIW-75 - pending a real Container Apps Environment quota increase), so this value has not yet been used for a real deploy."
  default     = "54ba3a475b4e90b77feb4fab396f07b64d925839"
}
