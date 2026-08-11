# Gateway Sentinel Rate Limiting

`mall-gateway` applies four protections in this order:

1. sanitize caller-controlled identity headers and derive `X-Real-IP`;
2. Sentinel Gateway rules enforce route-wide and per-IP limits;
3. JWT/Redis authentication establishes a trusted user id;
4. Sentinel parameter-flow rules enforce per-user limits on write and seckill routes.

## Profiles

The default `local` profile reads rules from:

- `sentinel/gateway-flow-rules.json`
- `sentinel/gateway-api-groups.json`
- `sentinel/user-param-flow-rules.json`

The `prod` profile reads the same three rule sets from Nacos. Sentinel rules are
per Gateway instance. Divide the desired service-wide budget by the number of
Gateway instances, or evaluate Sentinel cluster flow control when an exact
global quota is required.

## Local dashboard and Nacos

Start the governance services:

```powershell
docker compose --profile docker-governance up -d --build
```

- Sentinel Dashboard: `http://localhost:8858` (`sentinel` / `sentinel`)
- Nacos service API: `http://localhost:8848`
- Nacos 3 console: `http://localhost:8850`

Publish the repository-owned baseline rules to Nacos:

```powershell
.\scripts\sentinel\publish-rules.ps1
```

Then run Gateway with the production rule source:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:NACOS_ADDR = 'localhost:8848'
.\scripts\start-services.ps1 -Build
```

Create the Nacos namespace first when `NACOS_NAMESPACE` is non-empty. If Nacos
authentication is enabled, obtain an access token and pass it to the publisher:

```powershell
.\scripts\sentinel\publish-rules.ps1 -Namespace 'mall-prod' -AccessToken $token
```

Do not use the open-source Sentinel Dashboard's direct client rule editing as
the production source of truth: those edits are in-memory. Update Nacos (or a
deployment pipeline that publishes to Nacos) instead.

## Response contract

Blocked requests return:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 1
X-RateLimit-Resource: seckill-reserve
Content-Type: application/json
```

```json
{"code":429,"message":"request rate limit exceeded","data":null}
```

The Prometheus counter is `mall_gateway_rate_limit_blocked_total`, tagged by
`layer`, `resource`, and `block_type`.

## Readiness and operations

`/actuator/health/readiness` is `DOWN` when a required Gateway/API/user rule is
missing. Liveness does not depend on Nacos. Existing instances keep their
in-memory rules during a temporary Nacos outage, while a new unconfigured
instance should not receive traffic.

Only health endpoints are public. Metrics, Prometheus, and Sentinel actuator
details require an admin token and should additionally be restricted to the
operations network. Set `MALL_GATEWAY_TRUSTED_PROXIES` to a comma-separated list
of exact reverse-proxy IP addresses before trusting `X-Forwarded-For`; otherwise
the direct peer address is used.

Tune the checked-in thresholds with `docs/jmeter-plan.md`. Stateful POST retries
must retain the original `Idempotency-Key` and use randomized backoff after 429.
