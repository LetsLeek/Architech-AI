output "resource_group_name" {
  value = module.resource_group.name
}

output "resource_group_id" {
  value = module.resource_group.id
}

output "backend_fqdn" {
  value       = module.backend.fqdn
  description = "Real STAGING backend URL: https://<this>."
}

output "frontend_hostname" {
  value       = module.frontend.default_host_name
  description = "Real STAGING frontend URL: https://<this>."
}

output "frontend_deployment_token" {
  value       = module.frontend.api_key
  sensitive   = true
  description = "Set as the AZURE_STATIC_WEB_APPS_API_TOKEN_STAGING GitHub Actions secret for the frontend deployment workflow."
}

output "backend_api_key" {
  value       = random_password.backend_api_key.result
  sensitive   = true
  description = "AIW-185: the real X-API-Key value for this environment - read via `terraform output -raw backend_api_key` by backend-deploy-staging.yml's own post-deployment smoke test step."
}
