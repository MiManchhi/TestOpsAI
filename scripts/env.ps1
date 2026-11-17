# Dot-source to activate project-local tools in the current terminal.
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ToolchainRoot = Join-Path $ProjectRoot '.tools/toolchains'
$env:npm_config_cache = Join-Path $ProjectRoot '.tools/cache/npm'
$env:UV_CACHE_DIR = Join-Path $ProjectRoot '.tools/cache/uv'
$env:RUFF_CACHE_DIR = Join-Path $ProjectRoot '.tools/cache/ruff'
$env:MYPY_CACHE_DIR = Join-Path $ProjectRoot '.tools/cache/mypy'
$env:UV_SYSTEM_CERTS = 'true'
$env:UV_PYTHON_INSTALL_DIR = Join-Path $ToolchainRoot 'python-managed'
$LocalPython = Join-Path $ToolchainRoot 'python/python.exe'
if (Test-Path -LiteralPath $LocalPython) { $env:UV_PYTHON = $LocalPython }
$LocalJava = Join-Path $ToolchainRoot 'java'
if (Test-Path -LiteralPath (Join-Path $LocalJava 'bin/java.exe')) { $env:JAVA_HOME = $LocalJava }
$ToolPaths = @('npm', 'node', 'uv', 'python') | ForEach-Object { Join-Path $ToolchainRoot $_ } | Where-Object { Test-Path -LiteralPath $_ }
if ($env:JAVA_HOME) { $ToolPaths += Join-Path $env:JAVA_HOME 'bin' }
# Deduplicate to make repeated activation idempotent; preserve system fallbacks.
$env:PATH = (($ToolPaths + ($env:PATH -split ';')) | Select-Object -Unique) -join ';'

function Invoke-Checked {
    param([Parameter(Mandatory)][string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Command $Arguments" }
}
