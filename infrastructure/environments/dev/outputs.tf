output "resource_group_name" {
  value = module.resource_group.name
}

output "resource_group_id" {
  value = module.resource_group.id
}

output "container_app_environment_id" {
  value       = module.container_app_environment.id
  description = "AIW-74: STAGING reuses this same Container Apps Environment (this subscription's real quota only allows 1 total) rather than provisioning its own - the same shared-non-prod-infra exception already established for the NONPROD PostgreSQL server (AIW-72), never extended to PROD."
}

output "backend_fqdn" {
  value       = module.backend.fqdn
  description = "Real DEV backend URL: https://<this>."
}

output "frontend_hostname" {
  value       = module.frontend.default_host_name
  description = "Real DEV frontend URL: https://<this>."
}

output "frontend_deployment_token" {
  value       = module.frontend.api_key
  sensitive   = true
  description = "Set as the AZURE_STATIC_WEB_APPS_API_TOKEN GitHub Actions secret for the frontend deployment workflow."
}
