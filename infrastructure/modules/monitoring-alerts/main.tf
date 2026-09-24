# AIW-77: every metric name/dimension below was verified against a real, already-running
# Container App (ca-aiw-backend-dev) and PostgreSQL Flexible Server (psql-aiw-nonprod-swc) via
# `az monitor metrics list-definitions`, not assumed from documentation - e.g. "statusCodeCategory"
# as a real dimension on the "Requests" metric is what makes the HTTP-5xx alert below a native
# Azure Monitor metric alert rather than a log-query alert over hand-rolled application logs.
resource "azurerm_monitor_action_group" "this" {
  name                = "ag-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  short_name          = var.short_name

  dynamic "email_receiver" {
    for_each = var.email_receivers
    content {
      name                    = email_receiver.value.name
      email_address           = email_receiver.value.email_address
      use_common_alert_schema = true
    }
  }

  tags = var.tags
}

# --- Container App signals: HTTP errors, latency, restarts, CPU/memory ---

resource "azurerm_monitor_metric_alert" "http_5xx" {
  name                = "alert-${var.name_prefix}-http-5xx"
  resource_group_name = var.resource_group_name
  scopes              = [var.container_app_id]
  description         = "More than 10 HTTP 5xx responses in a 5-minute window."
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "Requests"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 10

    dimension {
      name     = "statusCodeCategory"
      operator = "Include"
      values   = ["5xx"]
    }
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "latency_high" {
  name                = "alert-${var.name_prefix}-latency-high"
  resource_group_name = var.resource_group_name
  scopes              = [var.container_app_id]
  description         = "Average response time above 3s for 5+ minutes."
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "ResponseTime"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 3000
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "restart_count" {
  name                = "alert-${var.name_prefix}-restart-count"
  resource_group_name = var.resource_group_name
  scopes              = [var.container_app_id]
  description         = "More than 3 container restarts in 15 minutes - a real crash-loop/repeated deployment-failure signal, not a single transient blip."
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT15M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "RestartCount"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 3
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "cpu_high" {
  name                = "alert-${var.name_prefix}-cpu-high"
  resource_group_name = var.resource_group_name
  scopes              = [var.container_app_id]
  description         = "Container App CPU usage above 90% for 5+ minutes."
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "CpuPercentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 90
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "memory_high" {
  name                = "alert-${var.name_prefix}-memory-high"
  resource_group_name = var.resource_group_name
  scopes              = [var.container_app_id]
  description         = "Container App memory usage above 90% for 5+ minutes."
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "MemoryPercentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 90
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

# --- PostgreSQL signals: database health ---

resource "azurerm_monitor_metric_alert" "database_down" {
  name                = "alert-${var.name_prefix}-db-down"
  resource_group_name = var.resource_group_name
  scopes              = [var.postgresql_server_id]
  description         = "PostgreSQL Flexible Server reporting not-alive - the most direct 'critical production unavailability' signal this project has."
  severity            = 0
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "is_db_alive"
    aggregation      = "Average"
    operator         = "LessThan"
    threshold        = 1
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}

resource "azurerm_monitor_metric_alert" "database_storage_high" {
  name                = "alert-${var.name_prefix}-db-storage-high"
  resource_group_name = var.resource_group_name
  scopes              = [var.postgresql_server_id]
  description         = "PostgreSQL storage above 85% - an outage this project can see coming and prevent, not just react to."
  severity            = 2
  frequency           = "PT15M"
  window_size         = "PT15M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "storage_percent"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 85
  }

  action {
    action_group_id = azurerm_monitor_action_group.this.id
  }

  tags = var.tags
}
