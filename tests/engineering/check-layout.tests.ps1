$ErrorActionPreference = 'Stop'
$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$FixtureRoot = Join-Path $RepositoryRoot ('.runtime/layout-tests/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $FixtureRoot -Force | Out-Null
$Files = git -C $RepositoryRoot ls-files --cached --others --exclude-standard
foreach ($relative in $Files) {
    $source = Join-Path $RepositoryRoot $relative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
    $target = Join-Path $FixtureRoot $relative
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}
$CheckScript = Join-Path $RepositoryRoot 'scripts/check-layout.ps1'
$script:Assertions = 0
function Assert-Layout([bool]$ShouldPass, [string]$Message) {
    $failed = $false
    try { & $CheckScript -RootPath $FixtureRoot | Out-Null } catch { $failed = $true }
    if ($failed -eq $ShouldPass) { throw "Unexpected result: $Message" }
    $script:Assertions++
    Write-Output "PASS: $Message"
}
try {
    Assert-Layout $true 'valid authored repository'
    $scratch = Join-Path $FixtureRoot 'scratch.py'
    Set-Content -LiteralPath $scratch -Value '# misplaced'
    Assert-Layout $false 'root source file rejected'
    Remove-Item -LiteralPath $scratch
    $unknown = Join-Path $FixtureRoot 'loose-notes'
    New-Item -ItemType Directory $unknown | Out-Null
    Assert-Layout $false 'unknown root directory rejected'
    Remove-Item -LiteralPath $unknown
    $readme = Join-Path $FixtureRoot 'README.md'
    Move-Item -LiteralPath $readme -Destination (Join-Path $FixtureRoot 'docs/README.md')
    Assert-Layout $false 'missing root entry rejected'
    Move-Item -LiteralPath (Join-Path $FixtureRoot 'docs/README.md') -Destination $readme
    $foreign = Join-Path $FixtureRoot 'apps/web/pom.xml'
    Set-Content -LiteralPath $foreign -Value '<project />'
    Assert-Layout $false 'Java configuration in web rejected'
    Remove-Item -LiteralPath $foreign
    $foreignApp = Join-Path $FixtureRoot 'apps/misc'
    New-Item -ItemType Directory $foreignApp | Out-Null
    Assert-Layout $false 'undeclared application rejected'
    Remove-Item -LiteralPath $foreignApp
    $sourceEntry = Join-Path $FixtureRoot 'apps/web/src/app/main.ts'
    Remove-Item -LiteralPath $sourceEntry
    Assert-Layout $false 'missing source entry rejected'
    Copy-Item -LiteralPath (Join-Path $RepositoryRoot 'apps/web/src/app/main.ts') -Destination $sourceEntry
    Set-Content -LiteralPath (Join-Path $FixtureRoot '.env.local') -Value '# local fixture'
    Assert-Layout $true 'local environment file accepted'
    $template = Join-Path $FixtureRoot '.env.unregistered.example'
    Set-Content -LiteralPath $template -Value '# extra template'
    # Unix dotfiles are hidden; exercise the same deletion constraint on Windows.
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        [IO.File]::SetAttributes($template, [IO.FileAttributes]::Hidden)
    }
    Assert-Layout $false 'unregistered root template rejected'
    Remove-Item -LiteralPath $template -Force
    $MemoryScript = Join-Path $RepositoryRoot 'scripts/memory.ps1'
    & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null
    Write-Output 'PASS: valid memory and coding rule navigation'
    $StylePath = Join-Path $FixtureRoot 'docs/development/CODE_STYLE.md'
    $StyleText = [IO.File]::ReadAllText($StylePath)
    Remove-Item -LiteralPath $StylePath
    $missingStyleRejected = $false
    try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $missingStyleRejected = $true }
    if (-not $missingStyleRejected) { throw 'Missing coding rules were not rejected' }
    [IO.File]::WriteAllText($StylePath, $StyleText, [Text.UTF8Encoding]::new($false))
    Write-Output 'PASS: missing coding rules rejected'
    $AgentPath = Join-Path $FixtureRoot 'AGENTS.md'
    $AgentText = [IO.File]::ReadAllText($AgentPath)
    [IO.File]::WriteAllText($AgentPath, $AgentText.Replace('docs/development/CODE_STYLE.md', 'coding-rules-not-linked'), [Text.UTF8Encoding]::new($false))
    $missingNavigationRejected = $false
    try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $missingNavigationRejected = $true }
    if (-not $missingNavigationRejected) { throw 'Missing rule navigation was not rejected' }
    [IO.File]::WriteAllText($AgentPath, $AgentText, [Text.UTF8Encoding]::new($false))
    Write-Output 'PASS: missing AGENTS coding-rule navigation rejected'
    $CollaborationPath = Join-Path $FixtureRoot 'docs/development/AI_COLLABORATION.md'
    $CollaborationText = [IO.File]::ReadAllText($CollaborationPath)
    Remove-Item -LiteralPath $CollaborationPath
    $missingCollaborationRejected = $false
    try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $missingCollaborationRejected = $true }
    if (-not $missingCollaborationRejected) { throw 'Missing collaboration guide was not rejected' }
    [IO.File]::WriteAllText($CollaborationPath, $CollaborationText, [Text.UTF8Encoding]::new($false))
    Write-Output 'PASS: missing collaboration guide rejected'
    $CurrentPath = Join-Path $FixtureRoot '.memory/CURRENT.md'
    $CurrentText = [IO.File]::ReadAllText($CurrentPath)
    foreach ($replacement in @('', '<!-- lane:web --><!-- lane:web -->')) {
        [IO.File]::WriteAllText($CurrentPath, $CurrentText.Replace('<!-- lane:web -->', $replacement), [Text.UTF8Encoding]::new($false))
        $invalidLaneRejected = $false
        try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $invalidLaneRejected = $true }
        if (-not $invalidLaneRejected) { throw 'Missing or duplicate CURRENT lane was not rejected' }
    }
    [IO.File]::WriteAllText($CurrentPath, $CurrentText, [Text.UTF8Encoding]::new($false))
    Write-Output 'PASS: missing and duplicate CURRENT lanes rejected'
    $HandoffPath = Join-Path $FixtureRoot '.memory/sessions/2099-01-01-01-fixture.md'
    $HandoffText = @"
# Handoff fixture
<!-- handoff:v1 -->
Task: TEST-001
Role: web
Baseline: fixture
Contract: fixture
Scope: fixture
<!-- checks -->
<!-- next -->
"@
    # Construct the localized status without relying on PS5 source encoding or CURRENT state.
    $HandoffText += "`n" + [char]0x72b6 + [char]0x6001 + ": IMPLEMENTED`n2099-01-01`n"
    [IO.File]::WriteAllText($HandoffPath, $HandoffText, [Text.UTF8Encoding]::new($false))
    & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null
    foreach ($field in @('Task', 'Role', 'Baseline', 'Contract', 'Scope')) {
        $invalidHandoff = [regex]::Replace($HandoffText, "(?m)^${field}:[^\r\n]*", '')
        [IO.File]::WriteAllText($HandoffPath, $invalidHandoff, [Text.UTF8Encoding]::new($false))
        $missingFieldRejected = $false
        try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $missingFieldRejected = $true }
        if (-not $missingFieldRejected) { throw "Missing handoff field was not rejected: $field" }
    }
    foreach ($invalidRole in @('unknown', 'web-extra')) {
        [IO.File]::WriteAllText($HandoffPath, $HandoffText.Replace('Role: web', "Role: $invalidRole"), [Text.UTF8Encoding]::new($false))
        $invalidRoleRejected = $false
        try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $invalidRoleRejected = $true }
        if (-not $invalidRoleRejected) { throw 'Invalid handoff role was not rejected' }
    }
    foreach ($marker in @('<!-- checks -->', '<!-- next -->')) {
        [IO.File]::WriteAllText($HandoffPath, $HandoffText.Replace($marker, ''), [Text.UTF8Encoding]::new($false))
        $missingMarkerRejected = $false
        try { & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null } catch { $missingMarkerRejected = $true }
        if (-not $missingMarkerRejected) { throw "Missing handoff marker was not rejected: $marker" }
    }
    Remove-Item -LiteralPath $HandoffPath
    Write-Output 'PASS: valid handoff accepted; missing fields, invalid roles and missing check/next markers rejected'
    & $MemoryScript -Mode Check -RootPath $FixtureRoot | Out-Null
    Write-Output "Layout regression tests passed: $script:Assertions assertions."
    Write-Output 'Memory regressions passed: navigation, lane ownership markers and versioned handoff structure.'
    $scriptCount = 0
    foreach ($sourceFile in Get-ChildItem (Join-Path $RepositoryRoot 'scripts'), (Join-Path $RepositoryRoot 'tests/engineering') -Filter '*.ps1' -File -Recurse) {
        $syntaxErrors = $null
        [Management.Automation.Language.Parser]::ParseFile($sourceFile.FullName, [ref]$null, [ref]$syntaxErrors) | Out-Null
        if ($syntaxErrors) { throw "PowerShell syntax errors in $($sourceFile.Name): $syntaxErrors" }
        $scriptCount++
    }
    Write-Output "PowerShell syntax checks passed: $scriptCount scripts."
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($FixtureRoot)
    $allowedRoot = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot '.runtime/layout-tests')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedFixture.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing unsafe fixture cleanup' }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
