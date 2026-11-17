# Installs locked dependencies; validates SDKs before creating local configuration.
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/doctor.ps1")
    & "$PSScriptRoot/initialize-local-env.ps1"
    Invoke-Checked npm @('ci')
    Invoke-Checked uv @('sync', '--project', 'apps/ai-service', '--locked', '--python', '3.12')
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/maven.ps1", '-B', '-ntp', '-DskipTests', 'test-compile')
} finally { Pop-Location }
