output "resource_group_name" {
  value = module.resource_group.name
}

output "postgresql_fqdn" {
  value       = module.postgresql.fqdn
  description = "Real connection host for both aiw_dev and aiw_staging - AIW-73 wires this into each environment's own Key-Vault-backed SPRING_DATASOURCE_URL."
}

output "dev_database_name" {
  value = postgresql_database.dev.name
}

output "dev_app_username" {
  value = postgresql_role.dev_app.name
}

output "dev_app_password" {
  value     = random_password.dev_app.result
  sensitive = true
}

output "staging_database_name" {
  value = postgresql_database.staging.name
}

output "staging_app_username" {
  value = postgresql_role.staging_app.name
}

output "staging_app_password" {
  value     = random_password.staging_app.result
  sensitive = true
}
