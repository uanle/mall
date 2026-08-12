# JMeter 压测计划

## 目标接口

通过 Gateway 调用：

```text
POST http://localhost:8080/api/seckill/1001/reserve
X-User-Id: ${userId}
Idempotency-Key: ${requestId}
```

## 线程组

- 线程数：从 100 开始，逐步提升到 300、500、1000。
- Ramp-up：30 秒。
- 持续时间：每轮 3 分钟。
- CSV 数据：
  - `userId`：唯一用户 ID。
  - `requestId`：唯一请求 ID。

## 指标

每轮结束后记录：

- TPS
- p95
- p99
- 错误率
- Redis CPU 使用率和命令延迟
- RabbitMQ ready/unacked 消息数
- MySQL 慢 SQL 和锁等待
- JVM 线程数和 GC 暂停

## 预期轮次

1. 直接写 MySQL 创建订单的基线版本。
2. Redis Lua 预扣库存 + RabbitMQ 异步创建订单。
3. 加入限流、热点 key 保护、消费者调优、MySQL 索引调优。

对比结果写入 [压测工作记录](perf-worklog.md)。
