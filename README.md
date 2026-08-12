# mall-trade

这是一个从零实现的后端项目，目标是展示高并发秒杀交易链路的核心工程能力。

业务边界参考 `macrozheng/mall` 的电商场景，但不复制、不重命名该仓库代码。

核心链路：

```text
user -> gateway -> product -> seckill qualification -> Redis Lua stock reservation -> RabbitMQ -> order -> MySQL
```

## 模块说明

- `mall-gateway`：统一入口，负责路由、JWT 会话校验、Sentinel 路由/IP/用户维度限流、接口访问审计。
- `mall-user`：注册、登录、JWT 签发、用户角色/等级管理、接口审计日志查询。
- `mall-product`：商品、库存、秒杀活动查询和管理接口。
- `mall-seckill`：Redis Lua 原子预扣库存、秒杀资格校验、MQ 投递。
- `mall-order`：RabbitMQ 消费、幂等创建订单、支付、完成、超时关闭。
- `mall-common`：共享 DTO、常量、响应结构和工具类。
- `mall-observability`：共享日志、链路追踪、Prometheus 指标和 HTTP 访问日志配置。

包目录约定：

- `controller`：REST API 入口。
- `service`：业务编排和事务边界。
- `entity`：MyBatis-Plus 表映射。
- `mapper`：MyBatis-Plus Mapper。
- `dto`：请求和响应 DTO。
- `config`：框架和中间件配置。
- `mq`：消息生产者和消费者。
- `job`：定时任务。
- `exception`：REST 异常处理。

## 本地启动

1. 安装 JDK 17 和 Maven 3.9+。

2. 准备本地 MySQL、Redis、RabbitMQ：

- MySQL：`localhost:3306`，数据库 `mall`，用户 `root`，密码 `root`。
- Redis：`localhost:6379`，密码 `root`。
- RabbitMQ：`localhost:5672`，管理界面 `http://localhost:15672`，用户 `mall`，密码 `mall`。

初始化 MySQL 表：

```powershell
Get-Content scripts\mysql\init.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

初始化脚本会创建 `mall_user`、`product`、`product_inventory`、`retail_order`、`inventory_deduct_log`、`seckill_activity`、`trade_order`、`stock_deduct_log`、`user_api_access_log` 等表。

默认用户：

- `admin / admin123`：`ADMIN`，`NONE`
- `user / user123`：`USER`，`NORMAL`
- `vip / user123`：`USER`，`VIP`
- `svip / user123`：`USER`，`SVIP`

3. 启动 RabbitMQ。

如果 Windows 上 RabbitMQ 是服务安装，可以在服务管理器中启动，也可以运行：

```powershell
rabbitmq-service.bat start
```

如果管理界面未启用，执行：

```powershell
rabbitmq-plugins enable rabbitmq_management
```

也可以用 Docker 启动基础设施：

```powershell
docker compose --profile docker-db --profile docker-mq up -d
```

Docker MySQL 默认创建数据库 `mall`，Redis 默认密码是 `root`，与各服务 `application.yml` 保持一致。如果之前已经用旧配置启动过 Docker 数据库，先停止并删除旧容器和数据卷后再重新启动，否则 MySQL 初始化变量不会再次生效：

```powershell
docker compose --profile docker-db down -v
docker compose --profile docker-db --profile docker-mq up -d
```

只启动 RabbitMQ：

```powershell
docker compose --profile docker-mq up -d
```

可选启动 Sentinel Dashboard 和 Nacos 规则中心：

```powershell
docker compose --profile docker-governance up -d --build
```

默认 `local` profile 会读取仓库内置 Sentinel 规则，不依赖 Nacos。Sentinel Dashboard 地址是 `http://localhost:8858`；Nacos 3 服务 API 是 `http://localhost:8848`，控制台是 `http://localhost:8850`。

4. 启动服务：

```powershell
.\scripts\start-services.ps1 -Build
```

默认会把结构化应用日志写入本地 `logs/*-app.log`。查看本地日志摘要：

```powershell
.\scripts\watch-logs.ps1
.\scripts\watch-logs.ps1 -Service gateway -Follow
```

Prometheus 指标可直接从各服务 actuator 读取，例如 Gateway：

```text
http://localhost:8080/actuator/prometheus
```

用户接口访问审计记录会写入 MySQL 表 `user_api_access_log`。如果你的数据库是在该表加入之前初始化的，执行迁移：

```powershell
Get-Content scripts\mysql\migrate-user-api-access-log.sql | & 'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -uroot -proot mall
```

通过 Gateway 使用管理员 Token 查询审计记录：

```powershell
curl.exe "http://localhost:8080/api/audit/access-logs?pageNum=1&pageSize=20&userId=2&success=false" -H "Authorization: Bearer $adminToken"
```

Docker 可视化观测栈是可选增强，只在需要 Grafana、Loki、Alloy、Tempo、Prometheus 时启动：

```powershell
docker compose --profile observability up -d
.\scripts\stop-services.ps1
.\scripts\start-services.ps1 -Build -TraceExport
```

Grafana 地址是 `http://localhost:3000`，本地账号 `admin / admin`。日志会从 `logs/*-app.log` 采集到 Loki，并可通过 `traceId` 关联 Tempo 调用链。更多配置见 [观测与日志](docs/observability.md)。

`-TraceExport` 用于打开 OTLP trace 导出；兼容旧参数 `-Observability`。

## 秒杀流程

登录普通用户并保存 Token：

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"user","password":"user123"}'

$token = $login.data.accessToken
```

登录管理员：

```powershell
$adminLogin = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'

$adminToken = $adminLogin.data.accessToken
```

创建秒杀活动：

```powershell
curl.exe -X POST "http://localhost:8080/internal/seckill/activities" `
  -H "Authorization: Bearer $adminToken" `
  -H "Content-Type: application/json" `
  -d '{"id":1101,"productId":2001,"startTime":"2026-08-11T00:00:00","endTime":"2026-08-31T23:59:59","totalStock":1000,"status":1}'
```

通过 Gateway 提交秒杀请求：

```powershell
$requestId = [guid]::NewGuid().ToString()

curl.exe -X POST "http://localhost:8080/api/seckill/1101/reserve" `
  -H "Authorization: Bearer $token" `
  -H "Idempotency-Key: $requestId"

Start-Sleep -Seconds 2
curl.exe "http://localhost:8080/api/orders/seckill-requests/$requestId"
```

支付并完成秒杀订单：

```powershell
$seckillOrder = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/orders/seckill-requests/$requestId" `
  -Headers @{ Authorization = "Bearer $token" }

$orderNo = $seckillOrder.data.order_no

curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/payments" -H "Authorization: Bearer $token"
curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/completion" -H "Authorization: Bearer $token"
```

如果由其他登录用户代付，调用专用代付接口：

```powershell
curl.exe -X POST "http://localhost:8080/api/orders/seckill-orders/$orderNo/help-payments" -H "Authorization: Bearer $otherUserToken"
```

## 普通订单流程

REST 路径：

```text
GET /api/products/{productId} -> POST /api/orders -> POST /api/orders/{orderNo}/payments -> POST /api/orders/{orderNo}/completion -> GET /api/orders/{orderNo}
```

运行冒烟测试：

```powershell
.\scripts\test-retail-order.ps1
```

Swagger UI：

- Gateway 聚合入口：`http://localhost:8080/swagger-ui/index.html`
- User 服务：`http://localhost:8084/swagger-ui/index.html`
- Product 服务：`http://localhost:8081/swagger-ui/index.html`
- Seckill 服务：`http://localhost:8082/swagger-ui/index.html`
- Order 服务：`http://localhost:8083/swagger-ui/index.html`

## 用户与权限

登录：

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"user","password":"user123"}'

$token = $login.data.accessToken
```

调用受保护接口：

```powershell
curl.exe "http://localhost:8080/api/users/me" -H "Authorization: Bearer $token"
curl.exe "http://localhost:8080/api/orders/stock-check?productId=2001&quantity=1" -H "Authorization: Bearer $token"
```

管理员接口要求 `ADMIN` 角色：

```powershell
$adminLogin = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin123"}'

$adminToken = $adminLogin.data.accessToken

curl.exe "http://localhost:8080/api/users?pageNum=1&pageSize=10" -H "Authorization: Bearer $adminToken"
```

用户登录和会话缓存：

- `cache:user:auth:{username}`：缓存登录校验信息，包含用于密码校验的 `passwordHash`。
- `cache:user:id:{userId}`：缓存当前用户资料，不包含 `passwordHash`。
- `cache:token:{jti}`：保存已登录 Token 会话；Gateway 先校验 JWT，再检查该 Redis key。
- `cache:user:tokens:{userId}`：保存用户活跃 Token ID，用于状态或权限变更后清理旧会话。
- 缓存值使用 Jackson Redis 序列化为 JSON。
- 用户缓存 TTL 默认 1800 秒；Token 会话 TTL 跟随 `mall.jwt.ttl-seconds`。
- 注册会写用户缓存；登录会写 Token 会话；登出会删除 Token 会话；管理员更新用户会清理用户缓存和活跃 Token 会话。
- 如果存在旧版本缓存，可删除 `cache:user:*` 和 `cache:token:*`，或等待 TTL 过期。

常用分页查询示例：

```powershell
curl.exe "http://localhost:8080/api/products?pageNum=1&pageSize=10&name=Phone&status=1"
curl.exe "http://localhost:8080/api/activities?pageNum=1&pageSize=10&productName=Phone&status=1&startFrom=2026-08-01T00:00:00&startTo=2026-08-31T23:59:59"
curl.exe "http://localhost:8080/api/inventories?pageNum=1&pageSize=10&productName=Phone&availableLte=100"
curl.exe "http://localhost:8080/api/seckill/activities?pageNum=1&pageSize=10&productName=Phone&status=1"
curl.exe "http://localhost:8080/api/seckill/stock-deduct-logs?pageNum=1&pageSize=10&activityId=1001&status=ORDER_CREATED"
curl.exe "http://localhost:8080/api/orders?pageNum=1&pageSize=10&userId=1&status=COMPLETED"
```

## 压测记录要求

每一轮压测结果记录到 [压测工作记录](docs/perf-worklog.md)，至少包含：

- 基线设计
- JMeter 配置
- TPS、p95、p99、错误率
- MySQL 慢 SQL 和索引
- Redis 延迟和热点 key
- RabbitMQ 发布/消费积压
- 线程池指标
- 优化决策
- 复测结果

## Gateway 限流

Gateway 在转发到下游服务之前完成限流：

- 路由级和调用方 IP 级限流使用 Sentinel Gateway 规则。
- 已登录写接口和秒杀接口额外使用可信 `userId` 参数限流。
- 被限流的请求返回 HTTP `429`、`Retry-After: 1` 和稳定 JSON 响应体。
- 本地规则位于 `mall-gateway/src/main/resources/sentinel`。
- `prod` profile 从 Nacos 读取持久化动态规则。

发布基线规则到本地 Nacos：

```powershell
.\scripts\sentinel\publish-rules.ps1
```

规则归属、生产启动、就绪检查、可信代理、指标和阈值调优见 [Gateway Sentinel 限流](docs/sentinel-rate-limiting.md)。
