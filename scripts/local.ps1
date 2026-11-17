param(
    [ValidateSet('start', 'stop', 'status', 'logs')][string]$Action = 'start',
    [switch]$SkipBuild
)
. "$PSScriptRoot/env.ps1"
if ($SkipBuild -and $Action -ne 'start') { throw '-SkipBuild is only valid with start.' }
Push-Location $ProjectRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker is required. Install and start Docker Desktop with the Linux engine.'
    }
    $engine = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to Docker. Start Docker Desktop and check Docker access.' }
    if ($engine -ne 'linux') { throw 'Switch Docker Desktop to the Linux container engine.' }
    if ($Action -eq 'start') {
        & "$PSScriptRoot/initialize-local-env.ps1"
    } elseif (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot '.env'))) {
        throw 'Local .env is missing. Run scripts/local.ps1 start first, or restore the original .env for existing data.'
    }
    # Validate without printing expanded credentials.
    Invoke-Checked docker @('compose', 'config', '--quiet')
    switch ($Action) {
        start {
            if (-not $SkipBuild) {
                Invoke-Checked docker @('compose', 'build', 'server', 'web', 'ai-service')
            }
            Invoke-Checked docker @('compose', 'up', '--no-build', '-d', '--wait', '--wait-timeout', '240')
            & "$PSScriptRoot/smoke.ps1"
            Write-Host 'TestOps AI is ready: http://127.0.0.1:8088'
            Write-Host 'Stop: scripts/local.ps1 stop | Status: scripts/local.ps1 status | Logs: scripts/local.ps1 logs'
        }
        stop { Invoke-Checked docker @('compose', 'stop') }
        status { Invoke-Checked docker @('compose', 'ps', '--all') }
        logs { Invoke-Checked docker @('compose', 'logs', '--follow', '--tail', '100') }
    }
} catch {
    Write-Host 'Local action failed. Check Docker output above; existing containers and data are retained.'
    Write-Host 'Diagnostics: scripts/local.ps1 status | scripts/local.ps1 logs'
    throw
} finally { Pop-Location }
