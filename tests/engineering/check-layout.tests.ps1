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
    Set-Content -LiteralPath (Join-Path $FixtureRoot '.env.unregistered.example') -Value '# extra template'
    Assert-Layout $false 'unregistered root template rejected'
    Remove-Item -LiteralPath (Join-Path $FixtureRoot '.env.unregistered.example')
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
    Write-Output "Layout regression tests passed: $script:Assertions assertions."
    Write-Output 'Memory/rule navigation regression tests passed: 3 assertions.'
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
