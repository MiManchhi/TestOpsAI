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
# Add blank provider settings once when upgrading an existing local installation.
$providerSettings = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $ConfigRoot '.env.example') |
    Where-Object { $_ -match '^TESTOPS_AI_[A-Z_]+=' }
$stream = [IO.File]::Open($ConfigPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
try {
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 1024, $true)
    try { $present = $reader.ReadToEnd() } finally { $reader.Dispose() }
    $missing = @($providerSettings | Where-Object {
        $name = ($_ -split '=', 2)[0]
        $present -notmatch "(?m)^$name="
    })
    if ($missing.Count) {
        [void]$stream.Seek(0, [IO.SeekOrigin]::End)
        $addition = [Text.Encoding]::UTF8.GetBytes("`n" + ($missing -join "`n") + "`n")
        $stream.Write($addition, 0, $addition.Length)
    }
} finally { $stream.Dispose() }
