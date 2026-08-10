param(
    [long]$UserId = 1,
    [long]$ProductId = 2001,
    [int]$Quantity = 1,
    [string]$Username = 'user',
    [string]$Password = 'user123'
)

$ErrorActionPreference = 'Stop'

Write-Host "Product detail:"
& curl.exe -s "http://localhost:8080/api/products/$ProductId"
Write-Host ''

Write-Host "Login: username=$Username"
$LoginBody = @{
    username = $Username
    password = $Password
} | ConvertTo-Json -Compress
$LoginBodyFile = Join-Path ([System.IO.Path]::GetTempPath()) ("mall-login-" + [guid]::NewGuid().ToString() + ".json")
Set-Content -LiteralPath $LoginBodyFile -Value $LoginBody -Encoding UTF8
try {
    $LoginResponse = & curl.exe -s -X POST "http://localhost:8080/api/auth/login" `
        -H "Content-Type: application/json" `
        --data-binary "@$LoginBodyFile"
} finally {
    Remove-Item -LiteralPath $LoginBodyFile -Force -ErrorAction SilentlyContinue
}
Write-Host $LoginResponse
$Token = ($LoginResponse | ConvertFrom-Json).data.accessToken
if (-not $Token) {
    throw 'Login failed; response did not contain data.accessToken.'
}

$IdempotencyKey = [guid]::NewGuid().ToString()
$Body = @{
    productId = $ProductId
    quantity = $Quantity
} | ConvertTo-Json -Compress
$BodyFile = Join-Path ([System.IO.Path]::GetTempPath()) ("mall-retail-order-" + [guid]::NewGuid().ToString() + ".json")
Set-Content -LiteralPath $BodyFile -Value $Body -Encoding UTF8

Write-Host "Create order: idempotencyKey=$IdempotencyKey"
try {
    $CreateResponse = & curl.exe -s -X POST "http://localhost:8080/api/orders" `
        -H "Content-Type: application/json" `
        -H "Authorization: Bearer $Token" `
        -H "Idempotency-Key: $IdempotencyKey" `
        --data-binary "@$BodyFile"
} finally {
    Remove-Item -LiteralPath $BodyFile -Force -ErrorAction SilentlyContinue
}
Write-Host $CreateResponse

$OrderNo = ($CreateResponse | ConvertFrom-Json).data.orderNo
if (-not $OrderNo) {
    throw 'Order creation failed; response did not contain data.orderNo.'
}

Write-Host "Pay order: orderNo=$OrderNo"
& curl.exe -s -X POST "http://localhost:8080/api/orders/$OrderNo/payments" -H "Authorization: Bearer $Token"
Write-Host ''

Write-Host "Complete order: orderNo=$OrderNo"
& curl.exe -s -X POST "http://localhost:8080/api/orders/$OrderNo/completion" -H "Authorization: Bearer $Token"
Write-Host ''

Write-Host "Query order: orderNo=$OrderNo"
& curl.exe -s "http://localhost:8080/api/orders/$OrderNo" -H "Authorization: Bearer $Token"
Write-Host ''
