output "resource_group_name" {
  value = module.resource_group.name
}

output "resource_group_id" {
  value = module.resource_group.id
}

output "container_registry_login_server" {
  value       = module.container_registry.login_server
  description = "Used by CI (docker tag/push) and by each environment's Container App image reference."
}

output "container_registry_id" {
  value = module.container_registry.id
}
