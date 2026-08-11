# Observability

The project uses one consistent stack for logs, metrics, and traces:

- Spring Boot structured logging with SLF4J and Logback.
- Micrometer Tracing with the OpenTelemetry bridge and OTLP exporter.
- Local JSON log files and actuator Prometheus metrics by default.
- Optional Grafana Alloy, Loki, Tempo, Prometheus, and Grafana for visual
  querying and trace correlation.

## Local startup

No Docker is required for the default local workflow. Start the services:

```powershell
.\scripts\start-services.ps1 -Build
```

If the services are already running, stop and restart them so the new JARs and
logging settings take effect:

```powershell
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build
```

Structured JSON logs are written to `logs/*-app.log`. For a compact local view:

```powershell
.\scripts\watch-logs.ps1
.\scripts\watch-logs.ps1 -Service gateway
.\scripts\watch-logs.ps1 -Service seckill -Follow
```

User API access audit records are also persisted to MySQL in
`user_api_access_log`. This table is for queryable user/API/result history, not
for full request or response body storage.

For an existing database, apply the migration:

```powershell
Get-Content scripts\mysql\migrate-user-api-access-log.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

Query through the gateway with an admin token:

```powershell
curl.exe "http://localhost:8080/api/audit/access-logs?pageNum=1&pageSize=20&path=/api/orders&success=false" -H "Authorization: Bearer $adminToken"
```

Local endpoints:

| Component | URL | Purpose |
| --- | --- | --- |
| Gateway logs | `logs/gateway-app.log` | Structured gateway application and access logs |
| User logs | `logs/user-app.log` | Structured user service logs |
| Product logs | `logs/product-app.log` | Structured product service logs |
| Seckill logs | `logs/seckill-app.log` | Structured seckill service logs |
| Order logs | `logs/order-app.log` | Structured order service logs |
| Gateway metrics | `http://localhost:8080/actuator/prometheus` | Gateway Prometheus metrics |
| User metrics | `http://localhost:8084/actuator/prometheus` | User service Prometheus metrics |
| Product metrics | `http://localhost:8081/actuator/prometheus` | Product service Prometheus metrics |
| Seckill metrics | `http://localhost:8082/actuator/prometheus` | Seckill service Prometheus metrics |
| Order metrics | `http://localhost:8083/actuator/prometheus` | Order service Prometheus metrics |

## Optional Docker stack

Use Docker only when you want a local visual observability stack:

```powershell
docker compose --profile observability up -d
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build -TraceExport
```

Docker stack endpoints:

| Component | URL | Purpose |
| --- | --- | --- |
| Grafana | `http://localhost:3000` | Logs, traces, and metrics (`admin / admin`) |
| Loki | `http://localhost:3100/ready` | Log storage readiness |
| Tempo | `http://localhost:3200/ready` | Trace storage readiness |
| Prometheus | `http://localhost:9090/targets` | Service scrape status |
| Alloy | `http://localhost:12345` | Log collection status |

The checked-in Grafana provisioning creates Loki, Tempo, and Prometheus data
sources and a `Mall application logs` dashboard automatically.

`-TraceExport` enables OTLP trace export to Tempo or another OTLP collector.
`-Observability` is still accepted as a backwards-compatible alias.

## Application behavior

`mall-observability` is a shared module used by every executable service. It
provides:

- JSON file logs in Spring Boot's `logstash` format.
- Log rotation at 100 MB, 14 days of history, and a 2 GB cap by default.
- One structured access event per HTTP request.
- `X-Trace-Id` on traced HTTP responses.
- A safe 500 response and a stack trace log for otherwise unhandled exceptions.
- Prometheus registry and OpenTelemetry tracing dependencies. OTLP trace export
  stays disabled unless `OTEL_TRACING_ENABLED=true` or `-TraceExport` is used.

Health and Prometheus scrape requests are excluded from access logs. Successful
request logs are configurable by sampling; errors and slow requests are always
logged. RabbitMQ producer and listener observations are enabled so the trace
context crosses the `mall-seckill` to `mall-order` message boundary.

Important structured fields include:

- Low-cardinality: `service`, `environment`, `level`, `event`.
- Correlation: `traceId`, `spanId`, `requestId`.
- Business context: `userId`, `orderNo`, `activityId`, `productId`.
- HTTP context: `httpMethod`, `path`, `status`, `durationMs`.

The database audit table stores similar request metadata: `user_id`,
`user_role`, `route_id`, `http_method`, `path`, `status`, `success`,
`duration_ms`, `client_ip`, `trace_id`, `request_id`, and `created_at`. It does
not store Authorization headers, cookies, passwords, request bodies, or response
bodies.

When the optional Docker stack is used, Alloy promotes only `service_name`,
`environment`, and `level` to Loki labels. High-cardinality identifiers remain
JSON fields and structured metadata.

## Useful LogQL queries

All errors from all services:

```logql
{service_name=~"mall-.*", level="ERROR"} | json
```

Follow a request across services:

```logql
{service_name=~"mall-.*"} | json | traceId="<trace-id>"
```

Follow a seckill business request across Redis reservation and RabbitMQ order
creation:

```logql
{service_name=~"mall-(seckill|order)"} | json | requestId="<request-id>"
```

Slow HTTP requests:

```logql
{service_name=~"mall-.*"} | json | event="http_request_completed" | durationMs >= 1000
```

## Runtime configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `OTEL_TRACING_ENABLED` | `false` | Enables OTLP trace export |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Tempo/collector OTLP HTTP endpoint |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Trace head-sampling probability |
| `MALL_ACCESS_LOG_ENABLED` | `true` | Enables HTTP access events |
| `MALL_ACCESS_LOG_SUCCESS_SAMPLE_RATE` | `1.0` | Sampling for successful, non-slow requests |
| `MALL_ACCESS_LOG_SLOW_THRESHOLD` | `1s` | Slow request threshold |
| `MALL_LOG_LEVEL` | `INFO` | Project package log level |
| `ROOT_LOG_LEVEL` | `INFO` | Root log level |
| `LOG_MAX_FILE_SIZE` | `100MB` | Rolled file size |
| `LOG_MAX_HISTORY` | `14` | Rolled file history |
| `LOG_TOTAL_SIZE_CAP` | `2GB` | Total retained files per service |

For load tests, set `MALL_ACCESS_LOG_SUCCESS_SAMPLE_RATE` to `0.01` or `0.1`.
HTTP errors and slow requests remain unsampled. Keep trace sampling at `1.0` for
local debugging and use a lower production value appropriate for traffic.

## Production guidance

- Emit structured JSON to standard output by setting
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`; let the platform collector read
  container output rather than writing application-owned files.
- Do not expose Grafana, Loki, Tempo, Prometheus, Alloy, or actuator endpoints
  to the public network. The local profile allows unauthenticated gateway
  Prometheus scraping only so the Docker development stack can reach it.
- Change Grafana credentials and configure persistent/object storage before
  production use.
- Never log passwords, JWTs, authorization headers, cookies, Redis cache
  payloads, or complete request/response bodies.
- Keep payment, inventory, and order audit records in transactional database
  tables. Operational logs are not a reliable accounting ledger.
