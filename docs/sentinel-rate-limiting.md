# Gateway Sentinel 限流

`mall-gateway` 按以下顺序提供四层保护：

1. 清理调用方可伪造的身份请求头，并派生可信 `X-Real-IP`。
2. Sentinel Gateway 规则执行路由维度和 IP 维度限流。
3. JWT 和 Redis 会话校验建立可信用户 ID。
4. Sentinel parameter-flow 规则对写接口和秒杀接口执行用户维度限流。

## Profile

默认 `local` profile 从以下文件读取规则：

- `sentinel/gateway-flow-rules.json`
- `sentinel/gateway-api-groups.json`
- `sentinel/user-param-flow-rules.json`

`prod` profile 从 Nacos 读取同样三组规则。Sentinel 规则作用于单个 Gateway 实例。如果需要控制整个服务集群的总限额，应把目标总预算除以 Gateway 实例数；如果需要严格全局配额，需要评估 Sentinel 集群流控。

## 本地 Dashboard 和 Nacos

启动治理组件：

```powershell
docker compose --profile docker-governance up -d --build
```

- Sentinel Dashboard：`http://localhost:8858`，账号 `sentinel / sentinel`
- Nacos 服务 API：`http://localhost:8848`
- Nacos 3 控制台：`http://localhost:8850`

把仓库维护的基线规则发布到 Nacos：

```powershell
.\scripts\sentinel\publish-rules.ps1
```

然后用生产规则源启动 Gateway：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:NACOS_ADDR = 'localhost:8848'
.\scripts\start-services.ps1 -Build
```

如果 `NACOS_NAMESPACE` 非空，需要先创建对应 namespace。Nacos 开启鉴权时，先获取 access token，再传给发布脚本：

```powershell
.\scripts\sentinel\publish-rules.ps1 -Namespace 'mall-prod' -AccessToken $token
```

不要把开源 Sentinel Dashboard 的客户端直连编辑作为生产规则事实来源，因为这些修改只在内存中生效。生产规则应更新到 Nacos，或由部署流水线发布到 Nacos。

## 响应约定

被限流的请求返回：

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 1
X-RateLimit-Resource: seckill-reserve
Content-Type: application/json
```

```json
{"code":429,"message":"request rate limit exceeded","data":null}
```

Prometheus 计数器是 `mall_gateway_rate_limit_blocked_total`，标签包括 `layer`、`resource`、`block_type`。

## 就绪检查与运维

当必需的 Gateway/API/user 规则缺失时，`/actuator/health/readiness` 返回 `DOWN`。Liveness 不依赖 Nacos。已有实例在 Nacos 短暂不可用时会保留内存中的规则；新的未配置实例不应接收流量。

只有 health 端点公开。Metrics、Prometheus、Sentinel actuator 详情都需要管理员 Token，并且生产环境还应限制在运维网络内访问。

信任 `X-Forwarded-For` 之前，必须把 `MALL_GATEWAY_TRUSTED_PROXIES` 设置为精确的反向代理 IP 列表，多个 IP 用英文逗号分隔。未配置时系统使用直连 peer 地址。

限流阈值应结合 [JMeter 压测计划](jmeter-plan.md) 调整。有状态 POST 重试必须复用原始 `Idempotency-Key`，收到 429 后使用随机退避。
