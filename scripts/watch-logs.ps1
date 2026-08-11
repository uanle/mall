param(
    [ValidateSet('all', 'gateway', 'user', 'product', 'seckill', 'order')]
    [string]$Service = 'all',
    [int]$Tail = 80,
    [switch]$Follow,
    [switch]$Raw
)

$ErrorActionPreference = 'Stop'

$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$LogDir = Join-Path $Workspace 'logs'

$LogNames = @{
    gateway = 'gateway-app.log'
    user = 'user-app.log'
    product = 'product-app.log'
    seckill = 'seckill-app.log'
    order = 'order-app.log'
}

if ($Service -eq 'all') {
    $Paths = $LogNames.Values | ForEach-Object { Join-Path $LogDir $_ }
} else {
    $Paths = @(Join-Path $LogDir $LogNames[$Service])
}

$ExistingPaths = $Paths | Where-Object { Test-Path -LiteralPath $_ }
if (-not $ExistingPaths) {
    throw "No log files found under $LogDir. Start services with scripts\start-services.ps1 first."
}

if ($Raw -or $Follow) {
    Get-Content -Path $ExistingPaths -Tail $Tail -Wait:$Follow
    return
}

$Rows = foreach ($Path in $ExistingPaths) {
    Get-Content -LiteralPath $Path -Tail $Tail | ForEach-Object {
        $Line = $_
        try {
            $Json = $Line | ConvertFrom-Json
            [pscustomobject]@{
                Time = $Json.PSObject.Properties['@timestamp'].Value
                Service = $Json.service
                Level = $Json.level
                Event = $Json.event
                TraceId = $Json.traceId
                Message = $Json.message
            }
        } catch {
            [pscustomobject]@{
                Time = ''
                Service = [IO.Path]::GetFileName($Path)
                Level = ''
                Event = ''
                TraceId = ''
                Message = $Line
            }
        }
    }
}

$Rows | Sort-Object Time | Format-Table -AutoSize -Wrap
