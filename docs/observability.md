# 观测与日志

项目使用一套统一的日志、指标和链路追踪方案：

- Spring Boot 结构化日志，底层使用 SLF4J 和 Logback。
- Micrometer Tracing，桥接 OpenTelemetry，支持 OTLP 导出。
- 默认使用本地 JSON 日志文件和 actuator Prometheus 指标。
- 可选使用 Grafana Alloy、Loki、Tempo、Prometheus、Grafana 做可视化查询和 trace 关联。

## 本地启动

默认本地工作流不需要 Docker，直接启动服务：

```powershell
.\scripts\start-services.ps1 -Build
```

如果服务已经在运行，先停止再启动，让新的 JAR 和日志配置生效：

```powershell
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build
```

结构化 JSON 日志会写入 `logs/*-app.log`。查看本地摘要：

```powershell
.\scripts\watch-logs.ps1
.\scripts\watch-logs.ps1 -Service gateway
.\scripts\watch-logs.ps1 -Service seckill -Follow
```

用户接口访问审计也会持久化到 MySQL 表 `user_api_access_log`。这张表用于查询“用户调用了什么接口、结果如何”，不保存完整请求体或响应体。

已有数据库需要执行迁移：

```powershell
Get-Content scripts\mysql\migrate-user-api-access-log.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

通过 Gateway 使用管理员 Token 查询审计记录：

```powershell
curl.exe "http://localhost:8080/api/audit/access-logs?pageNum=1&pageSize=20&path=/api/orders&success=false" -H "Authorization: Bearer $adminToken"
```

本地端点：

| 组件 | 地址 | 用途 |
| --- | --- | --- |
| Gateway 日志 | `logs/gateway-app.log` | Gateway 应用日志和访问日志 |
| User 日志 | `logs/user-app.log` | User 服务结构化日志 |
| Product 日志 | `logs/product-app.log` | Product 服务结构化日志 |
| Seckill 日志 | `logs/seckill-app.log` | Seckill 服务结构化日志 |
| Order 日志 | `logs/order-app.log` | Order 服务结构化日志 |
| Gateway 指标 | `http://localhost:8080/actuator/prometheus` | Gateway Prometheus 指标 |
| User 指标 | `http://localhost:8084/actuator/prometheus` | User 服务 Prometheus 指标 |
| Product 指标 | `http://localhost:8081/actuator/prometheus` | Product 服务 Prometheus 指标 |
| Seckill 指标 | `http://localhost:8082/actuator/prometheus` | Seckill 服务 Prometheus 指标 |
| Order 指标 | `http://localhost:8083/actuator/prometheus` | Order 服务 Prometheus 指标 |

## 可选 Docker 观测栈

只有需要本地可视化观测平台时才启动 Docker：

```powershell
docker compose --profile observability up -d
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build -TraceExport
```

Docker 观测栈端点：

| 组件 | 地址 | 用途 |
| --- | --- | --- |
| Grafana | `http://localhost:3000` | 日志、链路和指标查询，账号 `admin / admin` |
| Loki | `http://localhost:3100/ready` | 日志存储就绪检查 |
| Tempo | `http://localhost:3200/ready` | Trace 存储就绪检查 |
| Prometheus | `http://localhost:9090/targets` | 服务指标采集状态 |
| Alloy | `http://localhost:12345` | 日志采集状态 |

仓库内置的 Grafana provisioning 会自动创建 Loki、Tempo、Prometheus 数据源，以及 `Mall application logs` 仪表盘。

`-TraceExport` 会开启 OTLP trace 导出，目标可以是 Tempo 或其他 OTLP collector。`-Observability` 仍作为兼容别名保留。

## 应用行为

`mall-observability` 是所有可执行服务共同依赖的共享模块，提供：

- Spring Boot `logstash` 格式 JSON 文件日志。
- 默认 100 MB 滚动文件、保留 14 天、单服务总上限 2 GB。
- 每个 HTTP 请求完成后记录一条结构化访问事件。
- 已追踪 HTTP 响应带 `X-Trace-Id`。
- 未处理异常返回安全 500 响应，并记录异常栈。
- Prometheus registry 和 OpenTelemetry tracing 依赖。除非设置 `OTEL_TRACING_ENABLED=true` 或使用 `-TraceExport`，否则 OTLP trace 导出默认关闭。

健康检查和 Prometheus 抓取请求不会写入访问日志。成功请求可配置采样；错误请求和慢请求始终记录。RabbitMQ producer 和 listener 已开启 observation，因此 trace context 能跨越 `mall-seckill` 到 `mall-order` 的消息边界。

重要结构化字段：

- 低基数字段：`service`、`environment`、`level`、`event`
- 关联字段：`traceId`、`spanId`、`requestId`
- 业务上下文：`userId`、`orderNo`、`activityId`、`productId`
- HTTP 上下文：`httpMethod`、`path`、`status`、`durationMs`

数据库审计表保存类似的请求元数据：`user_id`、`user_role`、`route_id`、`http_method`、`path`、`status`、`success`、`duration_ms`、`client_ip`、`trace_id`、`request_id`、`created_at`。不会保存 Authorization header、Cookie、密码、请求体或响应体。

使用可选 Docker 观测栈时，Alloy 只把 `service_name`、`environment`、`level` 提升为 Loki label；`traceId`、`requestId`、`userId` 等高基数字段保留为 JSON 字段和 structured metadata。

## 常用 LogQL

查询所有服务错误：

```logql
{service_name=~"mall-.*", level="ERROR"} | json
```

按 traceId 串起一次跨服务请求：

```logql
{service_name=~"mall-.*"} | json | traceId="<trace-id>"
```

按秒杀业务 requestId 查询 Redis 预占和 RabbitMQ 创建订单链路：

```logql
{service_name=~"mall-(seckill|order)"} | json | requestId="<request-id>"
```

查询慢 HTTP 请求：

```logql
{service_name=~"mall-.*"} | json | event="http_request_completed" | durationMs >= 1000
```

## 运行时配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OTEL_TRACING_ENABLED` | `false` | 是否开启 OTLP trace 导出 |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Tempo 或 collector 的 OTLP HTTP endpoint |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Trace 头采样比例 |
| `MALL_ACCESS_LOG_ENABLED` | `true` | 是否开启 HTTP 访问事件 |
| `MALL_ACCESS_LOG_SUCCESS_SAMPLE_RATE` | `1.0` | 成功且非慢请求的采样比例 |
| `MALL_ACCESS_LOG_SLOW_THRESHOLD` | `1s` | 慢请求阈值 |
| `MALL_LOG_LEVEL` | `INFO` | 项目包日志级别 |
| `ROOT_LOG_LEVEL` | `INFO` | 根日志级别 |
| `LOG_MAX_FILE_SIZE` | `100MB` | 单个滚动日志文件大小 |
| `LOG_MAX_HISTORY` | `14` | 滚动日志保留天数 |
| `LOG_TOTAL_SIZE_CAP` | `2GB` | 单服务日志总保留上限 |

压测时可把 `MALL_ACCESS_LOG_SUCCESS_SAMPLE_RATE` 调整为 `0.01` 或 `0.1`。HTTP 错误和慢请求不会被采样跳过。本地调试可以保持 trace 采样为 `1.0`，生产环境按流量降低采样比例。

## 生产建议

- 容器环境建议设置 `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`，把结构化 JSON 输出到标准输出，由平台采集器读取。
- 不要把 Grafana、Loki、Tempo、Prometheus、Alloy 或 actuator 端点暴露到公网。`local` profile 只为 Docker 开发栈放开 Gateway Prometheus 抓取。
- 生产环境必须修改 Grafana 默认账号，并配置持久化或对象存储。
- 不记录密码、JWT、Authorization header、Cookie、Redis 缓存内容、完整请求体或完整响应体。
- 支付、库存、订单等审计应以事务数据库表为准；运行日志不是可靠的财务或库存账本。
