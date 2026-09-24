variable "backend_image_tag" {
  type        = string
  description = "Git commit SHA tag of the architech-backend image in ACR to deploy - never 'latest' (AIW-70's own tagging policy). Pass -var explicitly for a real redeploy to a newer SHA; the default below is only there so terraform-ci.yml's read-only `plan` (which never applies) doesn't need this wired in as a CI variable for a value it never acts on."
  # AIW-71's actual first real deploy - kept as the default (not a placeholder) so this always
  # reflects a real, once-deployed tag rather than an arbitrary string.
  default = "54ba3a475b4e90b77feb4fab396f07b64d925839"
}
