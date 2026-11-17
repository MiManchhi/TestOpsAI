# Shared by host setup and the Docker-only launcher. Never replace existing credentials.
$ErrorActionPreference = 'Stop'
$ConfigRoot = Split-Path $PSScriptRoot -Parent
$ConfigPath = Join-Path $ConfigRoot '.env'
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    $config = Get-Content -LiteralPath (Join-Path $ConfigRoot '.env.example') -Raw -Encoding UTF8
    foreach ($key in @('MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'REDIS_PASSWORD', 'QDRANT_API_KEY', 'JWT_SECRET')) {
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $secret = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        $config = [regex]::Replace($config, "(?m)^${key}=[^\r\n]*", "$key=$secret")
    }
    # CreateNew also protects credentials if another setup starts concurrently.
    $stream = [IO.File]::Open($ConfigPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $data = [Text.UTF8Encoding]::new($false).GetBytes($config)
        $stream.Write($data, 0, $data.Length)
    } finally { $stream.Dispose() }
    Write-Host 'Created local .env with random credentials (values hidden).'
}
