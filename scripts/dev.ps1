param([ValidateSet('web', 'server', 'ai', 'infra', 'all')][string]$Service = 'web')
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    switch ($Service) {
        web { Invoke-Checked npm @('run', 'dev') }
        server { Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/maven.ps1", 'spring-boot:run') }
        ai { Invoke-Checked uv @('run', '--project', 'apps/ai-service', '--locked', 'uvicorn', 'testops_ai.main:app', '--reload', '--host', '127.0.0.1', '--port', '8000') }
        infra {
            Invoke-Checked docker @('compose', 'up', '-d', '--wait', '--wait-timeout', '240', 'mysql', 'redis', 'qdrant')
            Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/smoke.ps1", '-InfraOnly')
        }
        all {
            & "$PSScriptRoot/local.ps1" start
        }
    }
} finally { Pop-Location }
