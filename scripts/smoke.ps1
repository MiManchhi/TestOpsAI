param([string]$BaseUrl = 'http://127.0.0.1:8088', [switch]$InfraOnly)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $stateLines = docker compose ps --all --format json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect container state' }
    $states = @($stateLines | ForEach-Object { $_ | ConvertFrom-Json })
    $services = @('mysql', 'redis', 'qdrant')
    if (-not $InfraOnly) { $services += @('server', 'ai-service', 'web') }
    $ports = @{ mysql = @(3306, 3306); redis = @(6379, 6379); qdrant = @(6333, 6333); web = @(80, 8088) }
    foreach ($service in $services) {
        $state = @($states | Where-Object { $_.Service -eq $service })
        if ($state.Count -ne 1 -or $state[0].State -ne 'running' -or $state[0].Health -ne 'healthy') {
            throw "Service is not healthy: $service"
        }
        $published = @($state[0].Publishers | Where-Object { $_.PublishedPort -gt 0 })
        if ($ports.ContainsKey($service)) {
            $expected = $ports[$service]
            if ($published.Count -ne 1 -or $published[0].URL -ne '127.0.0.1' -or
                $published[0].TargetPort -ne $expected[0] -or $published[0].PublishedPort -ne $expected[1]) {
                throw "Unexpected published port boundary: $service"
            }
        } elseif ($published.Count -ne 0) {
            throw "Internal service must not publish host ports: $service"
        }
    }

    $sql = 'SELECT 1;' | docker compose exec -T mysql sh -c 'MYSQL_PWD=$MYSQL_PASSWORD mysql --protocol=TCP -h127.0.0.1 -u$MYSQL_USER -D$MYSQL_DATABASE --batch --skip-column-names'
    if ($LASTEXITCODE -ne 0 -or ($sql -join '').Trim() -ne '1') { throw 'MySQL application-user query failed' }
    $pong = docker compose exec -T redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli ping'
    if ($LASTEXITCODE -ne 0 -or ($pong -join '').Trim() -ne 'PONG') { throw 'Redis authenticated ping failed' }
    docker compose exec -T qdrant bash -c "exec 3<>/dev/tcp/127.0.0.1/6333; printf 'GET /readyz HTTP/1.0\r\n\r\n' >&3; head -1 <&3 | grep -Eq '^HTTP/1\.[01] 200 '"
    if ($LASTEXITCODE -ne 0) { throw 'Qdrant readiness check failed' }
    Write-Output 'Infrastructure smoke passed: healthy services, loopback ports, MySQL query, Redis authentication, Qdrant readiness.'
    if ($InfraOnly) { return }

    $nginx = Invoke-WebRequest "$BaseUrl/healthz" -UseBasicParsing -TimeoutSec 10
    if ($nginx.StatusCode -ne 200 -or $nginx.Content.Trim() -ne 'OK') { throw 'Nginx health check failed' }
    $web = Invoke-WebRequest $BaseUrl -UseBasicParsing -TimeoutSec 10
    if ($web.StatusCode -ne 200 -or $web.Content -notmatch 'id="app"') { throw 'Web entrypoint check failed' }
    $assets = [regex]::Matches($web.Content, '(?:src|href)="(/assets/[^"?#]+\.(?:js|css))"')
    if (@($assets | Where-Object { $_.Groups[1].Value.EndsWith('.js') }).Count -eq 0) {
        throw 'Web entrypoint has no built JavaScript asset'
    }
    foreach ($asset in $assets) {
        $path = $asset.Groups[1].Value
        if ($path -match '\.\.') { throw 'Unexpected asset path' }
        $response = Invoke-WebRequest "$BaseUrl$path" -UseBasicParsing -TimeoutSec 10
        $expectedType = if ($path.EndsWith('.css')) { 'text/css' } else { '(?:text|application)/(?:javascript|ecmascript)' }
        if ($response.StatusCode -ne 200 -or $response.Headers['Content-Type'] -notmatch $expectedType) {
            throw "Built asset failed: $path"
        }
    }
    $health = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 10
    if ($health.status -ne 'UP' -or $health.PSObject.Properties.Name -contains 'components') {
        throw 'Java health must be UP without internal components'
    }
    $businessStatus = 0
    try {
        $business = Invoke-WebRequest "$BaseUrl/api/v1/users/me" -UseBasicParsing -TimeoutSec 10
        $businessStatus = [int]$business.StatusCode
    } catch {
        if (-not $_.Exception.Response) { throw }
        $businessStatus = [int]$_.Exception.Response.StatusCode
    }
    if ($businessStatus -ne 401) { throw 'Anonymous business access must return 401' }
    docker compose exec -T ai-service python -c "import json, urllib.request; urls = ['http://127.0.0.1:8000/health/live', 'http://127.0.0.1:8000/health/ready']; assert all(json.load(urllib.request.urlopen(url, timeout=5)) == {'status': 'UP'} for url in urls)"
    if ($LASTEXITCODE -ne 0) { throw 'AI health check failed' }
    Write-Output 'Full container smoke passed: six healthy services, port boundaries, built assets, Nginx, Java proxy/401 and AI health.'
} finally { Pop-Location }
