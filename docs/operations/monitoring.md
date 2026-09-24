# Monitoring, logging and alerts (AIW-77)

## Structured application logging

Every backend log line is a single JSON object (Spring Boot's built-in structured logging,
`logging.structured.format.console: logstash` in `application.yml` - no extra dependency), which
is what makes Container Apps' own console-log ingestion into Log Analytics
(`ContainerAppConsoleLogs`) queryable by field rather than needing to regex a plain-text line.
Real, verified output (from a real integration test run against `/actuator/health`):

```json
{"@timestamp":"2026-09-12T17:52:43.92Z","message":"GET /actuator/health -> 200 (17 ms)","logger_name":"ai.architech.backend.core.logging.RequestLoggingFilter","level":"INFO","httpPath":"/actuator/health","correlationId":"8c8e4209-6934-4568-8855-584a1f4f2d0d","httpMethod":"GET","durationMs":"17","httpStatus":"200"}
```

- **Correlation id** (`RequestCorrelationFilter`, AIW-59): a fresh UUID per request, always
  server-generated (never trusted from an incoming header), in MDC as `correlationId` for the
  request's full duration and echoed back as the `X-Correlation-Id` response header - the same id
  that appears in `ErrorResponse.correlationId` for a failed request, so a client-reported error
  ties directly back to its own server-side log lines.
- **Request summary line** (`RequestLoggingFilter`, AIW-77): exactly one structured line per
  request - `httpMethod`, `httpPath`, `httpStatus`, `durationMs`. Ordered to run after the
  correlation filter, so it always carries the same `correlationId`.
- **No secrets, ever**: this filter reads only the four fields above - never a header, a query
  string, or a body. There is no redaction step because there is nothing here to redact: an
  `Authorization` header or an API key can't leak from a log line that never reads it in the
  first place. Verified by `RequestLoggingFilterTests.neverLogsHeadersOrRequestBody` (a request
  carrying both a real-shaped bearer token and an API key in its body; asserts neither appears in
  the emitted log line).

## Querying logs (Log Analytics / KQL)

Every environment's Container App already sends console output to that environment's own Log
Analytics workspace (wired since AIW-71/74/75 via `log_analytics_workspace_id` on the Container
Apps Environment). Example queries against `ContainerAppConsoleLogs`:

```kql
// All 5xx responses in the last hour, newest first
ContainerAppConsoleLogs
| where ContainerAppName_s == "ca-aiw-backend-prod"
| extend parsed = parse_json(Log_s)
| where tostring(parsed.httpStatus) startswith "5"
| project TimeGenerated, parsed.httpMethod, parsed.httpPath, parsed.httpStatus, parsed.durationMs, parsed.correlationId
| order by TimeGenerated desc

// Every log line for one reported correlation id
ContainerAppConsoleLogs
| where ContainerAppName_s == "ca-aiw-backend-prod"
| extend parsed = parse_json(Log_s)
| where parsed.correlationId == "8c8e4209-6934-4568-8855-584a1f4f2d0d"
| order by TimeGenerated asc
```

## Metrics and alerts (`infrastructure/modules/monitoring-alerts`, PROD)

Every metric name and dimension below was verified against a real, already-running Container App
and PostgreSQL Flexible Server via `az monitor metrics list-definitions` before being written
into Terraform - not assumed from documentation.

| Alert | Signal | Threshold |
|---|---|---|
| `http-5xx` | `Requests` metric, `statusCodeCategory=5xx` dimension | > 10 in 5 min |
| `latency-high` | `ResponseTime` metric (native, per-status-code average) | > 3000 ms avg over 5 min |
| `restart-count` | `RestartCount` metric | > 3 in 15 min (crash-loop / repeated deployment failure) |
| `cpu-high` | `CpuPercentage` metric | > 90% avg over 5 min |
| `memory-high` | `MemoryPercentage` metric | > 90% avg over 5 min |
| `db-down` | PostgreSQL `is_db_alive` metric | < 1 over 5 min - the most direct "critical production unavailability" signal available |
| `db-storage-high` | PostgreSQL `storage_percent` metric | > 85% over 15 min - an outage this project can see coming, not just react to |

All seven alerts notify the same Azure Monitor action group (two email recipients - see
`infrastructure/environments/prod/main.tf`'s `module.monitoring_alerts` for the exact addresses).
The five Container-App-scoped alerts (`http-5xx`, `latency-high`, `restart-count`, `cpu-high`,
`memory-high`) reference `module.backend.id`, so - like the Container App itself - they stay
pinned until AIW-75's Container Apps Environment quota blocker is resolved (see
`prod-environment.md`); the two PostgreSQL-scoped alerts (`db-down`, `db-storage-high`) are
already real and applied today, independent of that blocker.

## Deliberately not built (documented, not an oversight)

- **A synthetic uptime/availability check** (e.g. an Application Insights availability test
  pinging the real URL from outside Azure) - the alerts above infer unavailability from
  restart-looping, 5xx rate and database health, which is a real but indirect signal. A true
  external ping-based check is a reasonable future increment once there's real production
  traffic to justify it, not built preemptively - same "document later scaling options rather
  than build them without need" posture as AIW-76's High Availability decision.
- **A dedicated metrics backend** (Application Insights / Micrometer→Azure Monitor exporter) -
  Container Apps' own native platform metrics (CPU/memory/restarts/requests/response time) plus
  the structured JSON console logs above already cover every metric this ticket's AC calls for,
  without the added infrastructure and cost of a separate APM product.
