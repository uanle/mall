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

## Start

1. Install JDK 17 and Maven 3.9+.
2. Prepare local MySQL and Redis:

- MySQL: `localhost:3306`, database `mall`, user `root`, password `root`.
- Redis: `localhost:6379`, password `root`.

Initialize MySQL tables:

```powershell
Get-Content scripts\mysql\init.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

3. Start RabbitMQ:

```powershell
docker compose up -d
```

If you want Docker MySQL and Docker Redis instead, run:

```powershell
docker compose --profile docker-db up -d
```

4. Start services in separate terminals:

```powershell
mvn -pl mall-product spring-boot:run
mvn -pl mall-seckill spring-boot:run
mvn -pl mall-order spring-boot:run
mvn -pl mall-gateway spring-boot:run
```

5. Initialize Redis stock:

```powershell
Invoke-RestMethod -Method Post http://localhost:8082/internal/seckill/1001/stock?quantity=1000
```

6. Submit a seckill request through the gateway:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/seckill/1001/reserve `
  -Headers @{"X-User-Id"="1"; "Idempotency-Key"=[guid]::NewGuid().ToString()}
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
