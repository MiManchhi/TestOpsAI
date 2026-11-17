# Integration acceptance for the current Compose project. Never deletes volumes.
$ErrorActionPreference = 'Stop'
$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$SmokeScript = Join-Path $RepositoryRoot 'scripts/smoke.ps1'
function Invoke-Compose([string[]]$ComposeArguments) {
    & docker compose @ComposeArguments
    if ($LASTEXITCODE -ne 0) { throw 'Compose acceptance command failed' }
}
function Get-VolumeIdentity {
    foreach ($service in @('mysql', 'redis', 'qdrant')) {
        $container = docker compose ps -q $service
        if ($LASTEXITCODE -ne 0 -or -not $container) { throw "Missing container: $service" }
        $mounts = docker inspect --format '{{json .Mounts}}' $container | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect volume mounts' }
        foreach ($mount in @($mounts | Where-Object { $_.Type -eq 'volume' })) {
            $created = docker volume inspect --format '{{.CreatedAt}}' $mount.Name
            if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect volume identity' }
            "$service/$($mount.Name)/$created"
        }
    }
}
Push-Location $RepositoryRoot
try {
    & $SmokeScript
    $javaUser = docker compose exec -T server id -u
    if ($LASTEXITCODE -ne 0 -or ($javaUser -join '').Trim() -eq '0') { throw 'Java must run as a non-root user' }
    docker compose exec -T ai-service python -c "import importlib.util, os, pathlib, shutil; assert os.geteuid() != 0; assert all(importlib.util.find_spec(name) is None for name in ['pytest', 'mypy', 'ruff']); assert shutil.which('uv') is None; assert not pathlib.Path('/app/src').exists(); assert not pathlib.Path('/app/.env').exists()"
    if ($LASTEXITCODE -ne 0) { throw 'AI runtime must be non-root without development tools or source workspace' }
    Write-Output 'PASS: non-root Java/AI and minimal AI runtime'
    $before = @(Get-VolumeIdentity | Sort-Object)
    if ($before.Count -ne 3) { throw 'Expected three infrastructure data volumes' }
    try {
        Invoke-Compose @('up', '--no-build', '-d', '--wait', '--wait-timeout', '240')
        & $SmokeScript
        Write-Output 'PASS: repeated Compose startup'
        Invoke-Compose @('stop')
        Invoke-Compose @('up', '--no-build', '-d', '--wait', '--wait-timeout', '240')
        & $SmokeScript
        $after = @(Get-VolumeIdentity | Sort-Object)
        if (Compare-Object $before $after) { throw 'Infrastructure volume identity changed' }
        Write-Output 'PASS: stop/start preserves all three data volumes and authenticated access'
        Invoke-Compose @('stop', 'ai-service')
        $rejected = $false
        try { & $SmokeScript } catch {
            if ($_.Exception.Message -ne 'Service is not healthy: ai-service') { throw }
            $rejected = $true
        }
        if (-not $rejected) { throw 'Smoke accepted a stopped AI service' }
        Write-Output 'PASS: stopped AI service rejected'
    } finally {
        Invoke-Compose @('up', '--no-build', '-d', '--wait', '--wait-timeout', '240')
    }
    & $SmokeScript
    Write-Output 'Container acceptance passed; all services restored, no volumes removed.'
} finally { Pop-Location }
