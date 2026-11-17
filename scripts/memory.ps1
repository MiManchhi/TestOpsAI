param(
    [ValidateSet('Check', 'Restore')][string]$Mode = 'Restore',
    [string]$RootPath = (Split-Path $PSScriptRoot -Parent),
    [ValidateSet('auto', 'integration', 'server', 'web', 'ai')][string]$Role = 'auto',
    [switch]$ListOnly
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
    'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md',
    'docs/development/AI_COLLABORATION.md'
)
foreach ($relative in $Required) {
    if (-not (Test-Path -LiteralPath (Join-Path $MemoryRoot $relative) -PathType Leaf)) {
        throw "Missing memory file: $relative"
    }
}
$AgentRules = [IO.File]::ReadAllText((Join-Path $MemoryRoot 'AGENTS.md'), $Utf8)
foreach ($reference in @('.memory/INDEX.md', '.memory/CURRENT.md', 'docs/development/CONTRIBUTING.md', 'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md', 'docs/development/AI_COLLABORATION.md')) {
    if (-not $AgentRules.Contains($reference)) { throw "Missing AGENTS reference: $reference" }
}
$CurrentText = [IO.File]::ReadAllText((Join-Path $MemoryRoot '.memory/CURRENT.md'), $Utf8)
foreach ($lane in @('integration', 'server', 'web', 'ai')) {
    if ([regex]::Matches($CurrentText, [regex]::Escape("<!-- lane:$lane -->")).Count -ne 1) {
        throw "CURRENT must contain exactly one lane marker: $lane"
    }
}
$Records = Get-ChildItem (Join-Path $MemoryRoot '.memory') -Filter '*.md' -Recurse |
    Where-Object { $_.FullName -notmatch '[\\/]archive[\\/]' }
foreach ($file in $Records) {
    $body = [IO.File]::ReadAllText($file.FullName, $Utf8)
    if ($body -notmatch '\d{4}-\d{2}-\d{2}') { throw "Missing date: $($file.Name)" }
    if ($body -notmatch '\u72b6\u6001[\uFF1A:]\s*(PLANNED|IN_PROGRESS|IMPLEMENTED|VERIFIED|DEPLOYED)') {
        throw "Missing valid status: $($file.Name)"
    }
    if ($file.Name -ne 'TEMPLATE.md' -and $body.Contains('<!-- handoff:v1 -->')) {
        foreach ($field in @('Task', 'Role', 'Baseline', 'Contract', 'Scope')) {
            if ($body -notmatch "(?m)^${field}:[ \t]*\S[^\r\n]*\r?$") {
                throw "Missing handoff field $field in $($file.Name)"
            }
        }
        if ($body -notmatch '(?m)^Role: (integration|server|web|ai)\r?$') {
            throw "Invalid handoff role in $($file.Name)"
        }
        foreach ($marker in @('<!-- checks -->', '<!-- next -->')) {
            if (-not $body.Contains($marker)) { throw "Missing handoff marker $marker in $($file.Name)" }
        }
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
    foreach ($relative in @('AGENTS.md', '.memory/INDEX.md', '.memory/CURRENT.md', 'docs/development/CONTRIBUTING.md', 'docs/development/DIRECTORY_LAYOUT.md', 'docs/development/CODE_STYLE.md', 'docs/development/AI_COLLABORATION.md')) {
        Write-Output "`n--- $relative ---"
        if (-not $ListOnly) { Write-Output ([IO.File]::ReadAllText((Join-Path $MemoryRoot $relative), $Utf8)) }
    }
    Write-Output "`n--- Git branch / working tree ---"
    $branch = git -C $MemoryRoot branch --show-current
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git branch' }
    Write-Output "Branch: $branch"
    $head = git -C $MemoryRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git HEAD' }
    Write-Output "HEAD: $head"
    git -C $MemoryRoot status --short
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status' }
    $resolvedRole = $Role
    if ($resolvedRole -eq 'auto') {
        $resolvedRole = switch -Regex ($branch) {
            '^(main|master)$' { 'integration'; break }
            '^codex/server-' { 'server'; break }
            '^codex/web-' { 'web'; break }
            '^codex/ai-' { 'ai'; break }
            default { 'unknown' }
        }
    }
    Write-Output "Reading role: $resolvedRole (not a write authorization)."
    foreach ($mainBranch in @('main', 'master')) {
        git -C $MemoryRoot show-ref --verify --quiet "refs/heads/$mainBranch"
        if ($LASTEXITCODE -eq 1) { continue }
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect local mainline' }
        $gap = git -C $MemoryRoot rev-list --count "HEAD..refs/heads/$mainBranch"
        if ($LASTEXITCODE -ne 0) { throw 'Cannot compare local mainline' }
        Write-Output "Local $mainBranch commits absent from HEAD: $gap (remote not fetched)."
    }
    $modules = switch ($resolvedRole) {
        'integration' { @('engineering', 'server', 'web', 'ai-service') }
        'server' { @('server') }
        'web' { @('web') }
        'ai' { @('ai-service') }
        default { @() }
    }
    $related = @($modules | ForEach-Object { ".memory/modules/$_.md" })
    # CURRENT links name the active handoffs; do not guess the latest task by filename.
    foreach ($match in [regex]::Matches($CurrentText, '\]\((sessions/[^)#]+\.md)(?:#[^)]*)?\)')) {
        $related += '.memory/' + $match.Groups[1].Value
    }
    foreach ($relative in @($related | Select-Object -Unique)) {
        Write-Output "`n--- $relative ---"
        if (-not $ListOnly) { Write-Output ([IO.File]::ReadAllText((Join-Path $MemoryRoot $relative), $Utf8)) }
    }
    Write-Output 'Next: read listed files in bounded batches, verify facts and assigned scope, then update handoff at checkpoints.'
    Write-Output 'A listing is not proof of reading. No Git writes or tests performed.'
}
