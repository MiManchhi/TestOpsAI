param(
    [ValidateSet('Check', 'Restore')][string]$Mode = 'Restore',
    [string]$RootPath = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
$MemoryRoot = (Resolve-Path -LiteralPath $RootPath).Path
$Utf8 = New-Object System.Text.UTF8Encoding($false, $true)
$Required = @(
    '.memory/INDEX.md', '.memory/CURRENT.md', '.memory/LESSONS.md',
    '.memory/LEARNING.md', '.memory/EVIDENCE.md',
    '.memory/modules/engineering.md', '.memory/modules/server.md',
    '.memory/modules/web.md', '.memory/modules/ai-service.md',
    'AGENTS.md', 'docs/development/CONTRIBUTING.md',
    'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md'
)
foreach ($relative in $Required) {
    if (-not (Test-Path -LiteralPath (Join-Path $MemoryRoot $relative) -PathType Leaf)) {
        throw "Missing memory file: $relative"
    }
}
$AgentRules = [IO.File]::ReadAllText((Join-Path $MemoryRoot 'AGENTS.md'), $Utf8)
foreach ($reference in @('.memory/INDEX.md', '.memory/CURRENT.md', 'docs/development/CONTRIBUTING.md', 'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md')) {
    if (-not $AgentRules.Contains($reference)) { throw "Missing AGENTS reference: $reference" }
}
$Records = Get-ChildItem (Join-Path $MemoryRoot '.memory') -Filter '*.md' -Recurse |
    Where-Object { $_.FullName -notmatch '[\\/]archive[\\/]' }
foreach ($file in $Records) {
    $body = [IO.File]::ReadAllText($file.FullName, $Utf8)
    if ($body -notmatch '\d{4}-\d{2}-\d{2}') { throw "Missing date: $($file.Name)" }
    if ($body -notmatch '\u72b6\u6001[\uFF1A:]\s*(PLANNED|IN_PROGRESS|IMPLEMENTED|VERIFIED|DEPLOYED)') {
        throw "Missing valid status: $($file.Name)"
    }
    foreach ($match in [regex]::Matches($body, '\[[^\]]+\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value
        if ($target -match '^(https?://|#)') { continue }
        $target = ($target -split '#', 2)[0]
        $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $target))
        if (-not $resolved.StartsWith($MemoryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Memory link escapes repository: $target"
        }
        if (-not (Test-Path -LiteralPath $resolved)) { throw "Broken link in $($file.Name): $target" }
    }
}
Write-Output "Memory checks passed ($($Records.Count) records; archive excluded)."
if ($Mode -eq 'Restore') {
    foreach ($relative in @('AGENTS.md', '.memory/INDEX.md', '.memory/CURRENT.md', 'docs/development/CONTRIBUTING.md', 'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md')) {
        Write-Output "`n--- $relative ---"
        Write-Output ([IO.File]::ReadAllText((Join-Path $MemoryRoot $relative), $Utf8))
    }
    Write-Output "`n--- Git branch / working tree ---"
    git -C $MemoryRoot branch --show-current
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git branch' }
    git -C $MemoryRoot status --short
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status' }
    Write-Output 'Next: verify current files, read related modules/sessions, then run task-specific checks.'
}
