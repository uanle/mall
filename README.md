# mall-trade

This repository is a from-scratch backend project for a resume-grade high-concurrency seckill trade path.

Reference scope: use `macrozheng/mall` for business boundaries only. Do not copy or rename that repository.

Core path:

`user -> gateway -> product -> seckill qualification -> Redis Lua stock reservation -> RabbitMQ -> order -> MySQL`

## Modules

- `mall-gateway`: route entry, static service routes.
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

The SQL creates the required tables: `product`, `product_inventory`, `retail_order`, `inventory_deduct_log`, `seckill_activity`, `trade_order`, and `stock_deduct_log`.

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

4. Start services:

```powershell
.\scripts\start-services.ps1 -Build
```

5. Initialize Redis stock:

```powershell
curl.exe -X POST "http://localhost:8082/internal/seckill/1001/stock?quantity=1000"
```

6. Submit a seckill request through the gateway:

```powershell
$requestId = [guid]::NewGuid().ToString()

curl.exe -X POST "http://localhost:8080/api/seckill/1001/reserve" `
  -H "X-User-Id: 1" `
  -H "Idempotency-Key: $requestId"

Start-Sleep -Seconds 2
curl.exe "http://localhost:8080/api/orders/$requestId"
```

## Retail Order Flow

REST path:

`GET /api/products/{productId} -> POST /api/orders -> POST /api/orders/{orderNo}/payments -> POST /api/orders/{orderNo}/completion`

Run the smoke test:

```powershell
.\scripts\test-retail-order.ps1
```

Swagger UI:

- Gateway aggregated Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Product service: `http://localhost:8081/swagger-ui/index.html`
- Seckill service: `http://localhost:8082/swagger-ui/index.html`
- Order service: `http://localhost:8083/swagger-ui/index.html`

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
