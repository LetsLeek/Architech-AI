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

output "postgresql_fqdn" {
  value       = module.postgresql.fqdn
  description = "Real connection host for aiw_prod - dedicated to PROD, never shared with NONPROD (AIW-76)."
}

output "prod_database_name" {
  value = postgresql_database.prod.name
}

output "prod_app_username" {
  value = postgresql_role.prod_app.name
}

output "prod_app_password" {
  value     = random_password.prod_app.result
  sensitive = true
}

output "backend_api_key" {
  value       = random_password.backend_api_key.result
  sensitive   = true
  description = "AIW-185: the real X-API-Key value for this environment - read via `terraform output -raw backend_api_key` by backend-deploy-prod.yml's own post-deployment smoke test step."
}

# backend_fqdn intentionally not exposed yet - the Container App itself is not yet created
# (AIW-75, pending a real Container Apps Environment quota increase; see
# docs/operations/prod-environment.md).
