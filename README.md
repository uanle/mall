# mall-trade

This repository is a from-scratch backend project for a resume-grade high-concurrency seckill trade path.

Reference scope: use `macrozheng/mall` for business boundaries only. Do not copy or rename that repository.

Core path:

`user -> gateway -> product -> seckill qualification -> Redis Lua stock reservation -> RabbitMQ -> order -> MySQL`

## Modules

- `mall-gateway`: route entry, JWT session validation, Sentinel route/IP/user rate limiting.
- `mall-user`: registration, login, JWT issuing, user role and level APIs.
- `mall-product`: product and seckill activity query APIs.
- `mall-seckill`: Redis Lua atomic stock reservation and MQ publishing.
- `mall-order`: RabbitMQ consumer, idempotent order creation, timeout close job.
- `mall-common`: shared DTOs, constants, API response.

Package convention:

- `controller`: REST API entrypoints.
- `service`: business orchestration and transaction boundary.
- `entity`: MyBatis-Plus table mappings.
- `mapper`: MyBatis-Plus mapper interfaces.
- `dto`: request/response DTOs.
- `config`: framework and middleware configuration.
- `mq`: message producers/consumers.
- `job`: scheduled jobs.
- `exception`: REST exception handlers.

## Start

1. Install JDK 17 and Maven 3.9+.
2. Prepare local MySQL, Redis, and RabbitMQ:

- MySQL: `localhost:3306`, database `mall`, user `root`, password `root`.
- Redis: `localhost:6379`, password `root`.
- RabbitMQ: `localhost:5672`, management UI `http://localhost:15672`, user `mall`, password `mall`.

Initialize MySQL tables:

```powershell
Get-Content scripts\mysql\init.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

The SQL creates the required tables: `mall_user`, `product`, `product_inventory`, `retail_order`, `inventory_deduct_log`, `seckill_activity`, `trade_order`, and `stock_deduct_log`.

Default users:

- admin / admin123: `ADMIN`, `NONE`
- user / user123: `USER`, `NORMAL`
- vip / user123: `USER`, `VIP`
- svip / user123: `USER`, `SVIP`

3. Start local RabbitMQ.

On Windows, if RabbitMQ is installed as a service, start it from Services or run:

```powershell
rabbitmq-service.bat start
```

Then enable the management UI if it is not already enabled:

```powershell
rabbitmq-plugins enable rabbitmq_management
```

If you want Docker infrastructure instead, run:

```powershell
docker compose --profile docker-db --profile docker-mq up -d
```

If you only want Docker RabbitMQ:

```powershell
docker compose --profile docker-mq up -d
```

Optional Sentinel Dashboard and Nacos rule center:

```powershell
docker compose --profile docker-governance up -d --build
```

The default `local` profile loads checked-in Sentinel rules and does not require
Nacos. Sentinel Dashboard is available at `http://localhost:8858`; Nacos 3 uses
`http://localhost:8848` for its service API and `http://localhost:8850` for its
console.

4. Start services:

```powershell
.\scripts\start-services.ps1 -Build
```

Structured application logs are written locally to `logs/*-app.log` by default.
View a compact local summary without Docker:

```powershell
.\scripts\watch-logs.ps1
.\scripts\watch-logs.ps1 -Service gateway -Follow
```

Prometheus metrics are available from each service, for example
`http://localhost:8080/actuator/prometheus` for the gateway.

User API access audit records are stored in MySQL table
`user_api_access_log`. If the database was initialized before this table was
added, run:

```powershell
Get-Content scripts\mysql\migrate-user-api-access-log.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

Query audit records with an admin token through the gateway:

```powershell
curl.exe "http://localhost:8080/api/audit/access-logs?pageNum=1&pageSize=20&userId=2&success=false" -H "Authorization: Bearer $adminToken"
```

Docker is only needed if you want the full optional visual stack with Grafana,
Loki, Alloy, Tempo, and Prometheus:

```powershell
docker compose --profile observability up -d
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build -TraceExport
```

Grafana is then available at `http://localhost:3000` with local credentials
`admin / admin`. Application logs are collected from `logs/*-app.log`, and
correlated with Tempo traces by `traceId`. See
[`docs/observability.md`](docs/observability.md) for configuration, production
guidance, and LogQL examples.

`-TraceExport` enables OTLP trace export for the optional stack. The legacy
`-Observability` switch is kept as an alias:

```powershell
.\scripts\start-services.ps1 -Build -TraceExport
```

5. Login and keep the token:

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"user","password":"user123"}'

$token = $login.data.accessToken
```

6. Create a seckill activity with admin token:

```powershell
$adminLogin = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'

$adminToken = $adminLogin.data.accessToken
```

```powershell
curl.exe -X POST "http://localhost:8080/internal/seckill/activities" `
  -H "Authorization: Bearer $adminToken" `
  -H "Content-Type: application/json" `
  -d '{"id":1101,"productId":2001,"startTime":"2026-08-11T00:00:00","endTime":"2026-08-31T23:59:59","totalStock":1000,"status":1}'
```

7. Submit a seckill request through the gateway:

```powershell
$requestId = [guid]::NewGuid().ToString()

curl.exe -X POST "http://localhost:8080/api/seckill/1101/reserve" `
  -H "Authorization: Bearer $token" `
  -H "Idempotency-Key: $requestId"

Start-Sleep -Seconds 2
curl.exe "http://localhost:8080/api/orders/seckill-requests/$requestId"
```

Pay and complete the seckill order:

```powershell
$seckillOrder = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/orders/seckill-requests/$requestId" `
  -Headers @{ Authorization = "Bearer $token" }

$orderNo = $seckillOrder.data.order_no

curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/payments" -H "Authorization: Bearer $token"
curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/completion" -H "Authorization: Bearer $token"
```

Use the dedicated help-payment endpoint when another logged-in user pays for the order:

```powershell
curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/help-payments" -H "Authorization: Bearer $otherUserToken"
```

## Retail Order Flow

REST path:

`GET /api/products/{productId} -> POST /api/orders -> POST /api/orders/{orderNo}/payments -> POST /api/orders/{orderNo}/completion -> GET /api/orders/{orderNo}`

Run the smoke test:

```powershell
.\scripts\test-retail-order.ps1
```

Swagger UI:

- Gateway aggregated Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- User service: `http://localhost:8084/swagger-ui/index.html`
- Product service: `http://localhost:8081/swagger-ui/index.html`
- Seckill service: `http://localhost:8082/swagger-ui/index.html`
- Order service: `http://localhost:8083/swagger-ui/index.html`

## User and Permission Flow

Login:

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"user","password":"user123"}'

$token = $login.data.accessToken
```

Call protected user/order/seckill APIs:

```powershell
curl.exe "http://localhost:8080/api/users/me" -H "Authorization: Bearer $token"
curl.exe "http://localhost:8080/api/orders/stock-check?productId=2001&quantity=1" -H "Authorization: Bearer $token"
```

Admin APIs require `ADMIN` role:

```powershell
$adminLogin = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'

$adminToken = $adminLogin.data.accessToken

curl.exe "http://localhost:8080/api/users?pageNum=1&pageSize=10" -H "Authorization: Bearer $adminToken"
```

User login/session cache:

- `cache:user:auth:{username}` caches login authentication JSON, including `passwordHash` for password verification.
- `cache:user:id:{userId}` caches current user profile JSON, excluding `passwordHash`.
- `cache:token:{jti}` stores the logged-in token session JSON. Gateway validates JWT first, then checks this Redis key.
- `cache:user:tokens:{userId}` stores the user's active token ids, used to remove old sessions when user status/permission changes.
- Cache value is stored as JSON through Jackson Redis serialization.
- User cache TTL defaults to 1800 seconds. Token session TTL follows `mall.jwt.ttl-seconds`.
- Registration writes user cache; login writes token session; logout deletes token session; admin updates evict user cache and active token sessions.
- If old cache values exist from earlier versions, delete `cache:user:*` and `cache:token:*` or wait for TTL expiration.

Common paging examples:

```powershell
curl.exe "http://localhost:8080/api/products?pageNum=1&pageSize=10&name=Phone&status=1"
curl.exe "http://localhost:8080/api/activities?pageNum=1&pageSize=10&productName=Phone&status=1&startFrom=2026-08-01T00:00:00&startTo=2026-08-31T23:59:59"
curl.exe "http://localhost:8080/api/inventories?pageNum=1&pageSize=10&productName=Phone&availableLte=100"
curl.exe "http://localhost:8080/api/seckill/activities?pageNum=1&pageSize=10&productName=Phone&status=1"
curl.exe "http://localhost:8080/api/seckill/stock-deduct-logs?pageNum=1&pageSize=10&activityId=1001&status=ORDER_CREATED"
curl.exe "http://localhost:8080/api/orders?pageNum=1&pageSize=10&userId=1&status=COMPLETED"
```

## Performance Worklog Target

Record each pressure-test round in `docs/perf-worklog.md`:

- baseline design
- JMeter config
- TPS, p95, p99, error rate
- MySQL slow SQL and indexes
- Redis latency and hot keys
- RabbitMQ publish/consume lag
- thread pool metrics
- optimization decision
- retest result

## Gateway Rate Limiting

Gateway limits are applied before downstream forwarding:

- route-wide and caller-IP limits use Sentinel Gateway rules;
- authenticated write/seckill routes also use trusted user-id parameter rules;
- blocked requests return HTTP `429`, `Retry-After: 1`, and a stable JSON body;
- local rules are stored under `mall-gateway/src/main/resources/sentinel`;
- the `prod` profile reads persistent dynamic rules from Nacos.

Publish the baseline files to a local Nacos instance with:

```powershell
.\scripts\sentinel\publish-rules.ps1
```

See [Gateway Sentinel rate limiting](docs/sentinel-rate-limiting.md) for rule
ownership, production startup, readiness behavior, trusted proxies, metrics,
and threshold tuning.
