param(
    [string]$NacosAddress = 'http://localhost:8848',
    [string]$Namespace = '',
    [string]$AccessToken = ''
)

$ErrorActionPreference = 'Stop'

$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$RuleDirectory = Join-Path $Workspace 'mall-gateway\src\main\resources\sentinel'
$GroupId = 'SENTINEL_GROUP'
$Rules = @(
    @{ DataId = 'mall-gateway-gw-flow-rules.json'; File = 'gateway-flow-rules.json' },
    @{ DataId = 'mall-gateway-gw-api-groups.json'; File = 'gateway-api-groups.json' },
    @{ DataId = 'mall-gateway-user-param-flow-rules.json'; File = 'user-param-flow-rules.json' }
)

foreach ($Rule in $Rules) {
    $RulePath = Join-Path $RuleDirectory $Rule.File
    if (-not (Test-Path -LiteralPath $RulePath)) {
        throw "Missing Sentinel rule file: $RulePath"
    }

    $Body = @{
        dataId = $Rule.DataId
        group = $GroupId
        type = 'json'
        content = Get-Content -Raw -LiteralPath $RulePath
    }
    if ($Namespace) {
        $Body.tenant = $Namespace
    }
    if ($AccessToken) {
        $Body.accessToken = $AccessToken
    }

    $Uri = "$($NacosAddress.TrimEnd('/'))/nacos/v1/cs/configs"
    $Published = Invoke-RestMethod -Method Post -Uri $Uri `
        -ContentType 'application/x-www-form-urlencoded' -Body $Body
    if ($Published -ne $true -and $Published -ne 'true') {
        throw "Nacos rejected Sentinel rule $($Rule.DataId): $Published"
    }
    Write-Host "Published $($Rule.DataId) to $GroupId"
}
