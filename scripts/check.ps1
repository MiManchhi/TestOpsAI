. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/memory.ps1", '-Mode', 'Check')
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/check-layout.ps1")
    Invoke-Checked powershell @('-NoProfile', '-File', "$ProjectRoot/tests/engineering/check-layout.tests.ps1")
    Invoke-Checked npm @('run', 'format:check')
    Invoke-Checked npm @('run', 'check')
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/maven.ps1", '-B', '-ntp', 'verify')
    Push-Location apps/ai-service
    try {
        Invoke-Checked uv @('lock', '--check')
        Invoke-Checked uv @('run', '--locked', 'ruff', 'check', '.')
        Invoke-Checked uv @('run', '--locked', 'ruff', 'format', '--check', '.')
        Invoke-Checked uv @('run', '--locked', 'ruff', 'check', '--config', 'pyproject.toml', "$ProjectRoot/scripts/smoke-local.py")
        Invoke-Checked uv @('run', '--locked', 'ruff', 'format', '--check', '--config', 'pyproject.toml', "$ProjectRoot/scripts/smoke-local.py")
        Invoke-Checked uv @('run', '--locked', 'mypy')
        Invoke-Checked uv @('run', '--locked', 'mypy', '--strict', "$ProjectRoot/scripts/smoke-local.py")
        Invoke-Checked uv @('run', '--locked', 'pytest')
        Invoke-Checked uv @('build', '--out-dir', 'dist')
    } finally { Pop-Location }
} finally { Pop-Location }
