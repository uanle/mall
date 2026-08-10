$ErrorActionPreference = 'Stop'

$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Ports = 8080, 8081, 8082, 8083, 8084

$Stopped = @()
$Connections = Get-NetTCPConnection -LocalPort $Ports -State Listen -ErrorAction SilentlyContinue

foreach ($Connection in $Connections) {
    $Process = Get-Process -Id $Connection.OwningProcess -ErrorAction SilentlyContinue
    if (-not $Process -or $Process.ProcessName -ne 'java') {
        Write-Host "Port $($Connection.LocalPort) is not owned by a Java process, skipping."
        continue
    }

    Stop-Process -Id $Connection.OwningProcess -Force
    $Stopped += [pscustomobject]@{
        Port = $Connection.LocalPort
        ProcessId = $Connection.OwningProcess
    }
}

$ProjectJavaProcesses = Get-CimInstance Win32_Process -Filter "name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*$Workspace*" }

foreach ($Process in $ProjectJavaProcesses) {
    if ($Stopped.ProcessId -contains $Process.ProcessId) {
        continue
    }

    Stop-Process -Id $Process.ProcessId -Force
    $Stopped += [pscustomobject]@{
        Port = 'workspace'
        ProcessId = $Process.ProcessId
    }
}

if ($Stopped.Count -eq 0) {
    Write-Host 'No mall service Java processes were running.'
} else {
    $Stopped | Sort-Object Port | Format-Table -AutoSize
}
