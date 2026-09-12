output "resource_group_name" {
  value = module.resource_group.name
}

output "resource_group_id" {
  value = module.resource_group.id
}

output "frontend_hostname" {
  value       = module.frontend.default_host_name
  description = "Real PROD frontend URL: https://<this>."
}

output "frontend_deployment_token" {
  value       = module.frontend.api_key
  sensitive   = true
  description = "Set as the AZURE_STATIC_WEB_APPS_API_TOKEN_PROD GitHub Actions secret for the frontend deployment workflow."
}

# backend_fqdn intentionally not exposed yet - the Container App itself is not yet created
# (AIW-75, pending a real Container Apps Environment quota increase; see
# docs/operations/prod-environment.md).
