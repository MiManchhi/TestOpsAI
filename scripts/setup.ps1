# Installs locked dependencies; validates SDKs before creating local configuration.
. "$PSScriptRoot/env.ps1"
Push-Location $ProjectRoot
try {
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/doctor.ps1")
    if (-not (Test-Path '.env')) {
        $config = Get-Content '.env.example' -Raw
        foreach ($key in @('MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'REDIS_PASSWORD', 'QDRANT_API_KEY')) {
            $bytes = New-Object byte[] 32
            $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
            $secret = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
            $config = $config.Replace("$key=`n", "$key=$secret`n").Replace("$key=`r`n", "$key=$secret`r`n")
        }
        [IO.File]::WriteAllText((Join-Path $ProjectRoot '.env'), $config)
    }
    Invoke-Checked npm @('ci')
    Invoke-Checked uv @('sync', '--project', 'apps/ai-service', '--locked', '--python', '3.12')
    Invoke-Checked powershell @('-NoProfile', '-File', "$PSScriptRoot/maven.ps1", '-B', '-ntp', '-DskipTests', 'test-compile')
} finally { Pop-Location }
