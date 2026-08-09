param(
    [long]$UserId = 1,
    [long]$ProductId = 2001,
    [int]$Quantity = 1
)

$ErrorActionPreference = 'Stop'

Write-Host "Product detail:"
& curl.exe -s "http://localhost:8080/api/products/$ProductId"
Write-Host ''

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
        -H "X-User-Id: $UserId" `
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
& curl.exe -s -X POST "http://localhost:8080/api/orders/$OrderNo/payments"
Write-Host ''

Write-Host "Complete order: orderNo=$OrderNo"
& curl.exe -s -X POST "http://localhost:8080/api/orders/$OrderNo/completion"
Write-Host ''

Write-Host "Query order: orderNo=$OrderNo"
& curl.exe -s "http://localhost:8080/api/orders/$OrderNo"
Write-Host ''
