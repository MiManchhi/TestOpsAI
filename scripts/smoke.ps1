param([string]$BaseUrl = 'http://127.0.0.1:8088', [switch]$InfraOnly)
$ErrorActionPreference = 'Stop'
if ($InfraOnly) {
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        $stateLines = docker compose ps --format json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect infrastructure state' }
        $states = @($stateLines | ForEach-Object { $_ | ConvertFrom-Json })
        foreach ($service in @('mysql', 'redis', 'qdrant')) {
            $state = @($states | Where-Object { $_.Service -eq $service })
            if ($state.Count -ne 1 -or $state[0].State -ne 'running' -or $state[0].Health -ne 'healthy') {
                throw "Infrastructure is not healthy: $service"
            }
        }
        $sql = 'SELECT 1;' | docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_PASSWORD mysql --protocol=TCP -h127.0.0.1 -u$MYSQL_USER -D$MYSQL_DATABASE --batch --skip-column-names'
        if ($LASTEXITCODE -ne 0 -or ($sql -join '').Trim() -ne '1') { throw 'MySQL application-user query failed' }
        $pong = docker compose exec -T redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli ping'
        if ($LASTEXITCODE -ne 0 -or ($pong -join '').Trim() -ne 'PONG') { throw 'Redis authenticated ping failed' }
        $ready = Invoke-WebRequest 'http://127.0.0.1:6333/readyz' -UseBasicParsing -TimeoutSec 5
        if ($ready.StatusCode -ne 200) { throw 'Qdrant readiness failed' }
        Write-Output 'Infrastructure smoke passed: three healthy services, MySQL query, Redis authentication, Qdrant readiness.'
    } finally { Pop-Location }
    return
}
$response = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 10
if ($response.status -ne 'UP') { throw 'Java health check failed' }
$web = Invoke-WebRequest $BaseUrl -UseBasicParsing -TimeoutSec 10
if ($web.StatusCode -ne 200) { throw 'Web health check failed' }
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    docker compose exec -T ai-service python -c "import urllib.request; assert b'UP' in urllib.request.urlopen('http://127.0.0.1:8000/health/ready', timeout=5).read()"
    if ($LASTEXITCODE -ne 0) { throw 'AI health check failed' }
    docker compose exec -T qdrant bash -c 'exec 3<>/dev/tcp/127.0.0.1/6333; printf "GET /readyz HTTP/1.0\r\n\r\n" >&3; head -1 <&3 | grep -q "200"'
    if ($LASTEXITCODE -ne 0) { throw 'Qdrant readiness check failed' }
} finally { Pop-Location }
Write-Output 'All smoke checks passed.'
