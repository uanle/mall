param(
    [switch]$Build
)

$ErrorActionPreference = 'Stop'

$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$LogDir = Join-Path $Workspace 'logs'

if ($env:JAVA_HOME) {
    $JavaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $JavaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    $JavaExe = if ($JavaCommand) { $JavaCommand.Source } else { $null }
}

if (-not $JavaExe -or -not (Test-Path -LiteralPath $JavaExe)) {
    throw 'Java was not found. Set JAVA_HOME to JDK 17 or add java.exe to Path.'
}

if ($Build) {
    Push-Location $Workspace
    try {
        & mvn package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$Services = @(
    @{ Name = 'mall-user';    Port = 8084; Jar = 'mall-user\target\mall-user-0.1.0-SNAPSHOT.jar';       Log = 'user-app.log' },
    @{ Name = 'mall-order';   Port = 8083; Jar = 'mall-order\target\mall-order-0.1.0-SNAPSHOT.jar';     Log = 'order-app.log' },
    @{ Name = 'mall-product'; Port = 8081; Jar = 'mall-product\target\mall-product-0.1.0-SNAPSHOT.jar'; Log = 'product-app.log' },
    @{ Name = 'mall-seckill'; Port = 8082; Jar = 'mall-seckill\target\mall-seckill-0.1.0-SNAPSHOT.jar'; Log = 'seckill-app.log' },
    @{ Name = 'mall-gateway'; Port = 8080; Jar = 'mall-gateway\target\mall-gateway-0.1.0-SNAPSHOT.jar'; Log = 'gateway-app.log' }
)

foreach ($Service in $Services) {
    $Existing = Get-NetTCPConnection -LocalPort $Service.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($Existing) {
        Write-Host "$($Service.Name) already listens on port $($Service.Port), skipping."
        continue
    }

    $JarPath = Join-Path $Workspace $Service.Jar
    if (-not (Test-Path -LiteralPath $JarPath)) {
        throw "Missing jar: $JarPath. Run scripts\start-services.ps1 -Build or mvn package -DskipTests first."
    }

    $LogPath = Join-Path $LogDir $Service.Log
    $Arguments = @('-jar', $JarPath, "--logging.file.name=$LogPath")
    Start-Process -FilePath $JavaExe -ArgumentList $Arguments -WorkingDirectory $Workspace -WindowStyle Hidden
    Write-Host "Started $($Service.Name) on port $($Service.Port). Log: $LogPath"
}

Start-Sleep -Seconds 12

foreach ($Service in $Services) {
    $HealthUrl = "http://localhost:$($Service.Port)/actuator/health"
    try {
        $Health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 5
        Write-Host "$($Service.Name) health: $($Health.status)"
    } catch {
        Write-Host "$($Service.Name) health check failed: $HealthUrl"
    }
}
