# Test script for iteration API
$body = @{
    optimizationAlgorithm = "EPSO"
    iterations = 3
    numHosts = 5
    numVMs = 10
    numPesPerHost = 2
    peMips = 2000
    ramPerHost = 2048
    bwPerHost = 10000
    storagePerHost = 100000
    vmMips = 1000
    vmPes = 2
    vmRam = 1024
    vmBw = 1000
    vmSize = 10000
    vmScheduler = "TimeShared"
    numCloudlets = 20
    workloadType = "Random"
    useDefaultWorkload = $false
} | ConvertTo-Json

$headers = @{
    "Content-Type" = "application/json"
}

Write-Host "Testing iteration API with 3 iterations..." -ForegroundColor Cyan
Write-Host "Request body:" -ForegroundColor Yellow
Write-Host $body

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8081/api/run-iterations" -Method Post -Body $body -Headers $headers
    Write-Host "`nSuccess! Response:" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 10
} catch {
    Write-Host "`nError occurred:" -ForegroundColor Red
    Write-Host $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        Write-Host "Error details:" -ForegroundColor Red
        Write-Host $errorBody
    }
}
