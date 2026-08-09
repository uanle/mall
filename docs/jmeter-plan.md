# JMeter Plan

## Target API

Use gateway endpoint:

```text
POST http://localhost:8080/api/seckill/1001/reserve
X-User-Id: ${userId}
Idempotency-Key: ${requestId}
```

## Thread Group

- Threads: start with 100, then 300, 500, 1000.
- Ramp-up: 30 seconds.
- Duration: 3 minutes per round.
- CSV data:
  - `userId`: unique user id.
  - `requestId`: unique request id.

## Metrics

Record these after every round:

- TPS
- p95
- p99
- error rate
- Redis used CPU and command latency
- RabbitMQ ready/unacked messages
- MySQL slow SQL and lock waits
- JVM thread count and GC pause

## Expected Rounds

1. Direct MySQL order creation baseline.
2. Redis Lua stock reservation + RabbitMQ async order creation.
3. Add rate limiting, hot key protection, consumer tuning, MySQL index tuning.

Write the comparison into `docs/perf-worklog.md`.
