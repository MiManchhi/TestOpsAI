param([string]$RootPath = (Split-Path $PSScriptRoot -Parent))
$ErrorActionPreference = 'Stop'
$LayoutRoot = (Resolve-Path -LiteralPath $RootPath).Path
$RootFiles = @('README.md', 'AGENTS.md', 'LICENSE', 'package.json', 'package-lock.json', 'compose.yaml', '.dockerignore', '.editorconfig', '.env.example', '.gitattributes', '.gitignore', '.npmrc', '.nvmrc', '.prettierignore')
$RootDirectories = @('apps', 'docs', 'infra', 'scripts', 'tests', 'samples', '.memory', '.github', '.vscode')
$GeneratedDirectories = @('.git', '.tools', 'node_modules', '.runtime')
$Problems = [Collections.Generic.List[string]]::new()
function Test-Level([string]$Relative, [string[]]$Files, [string[]]$Directories, [string[]]$Required) {
    $folder = if ($Relative) { Join-Path $LayoutRoot $Relative } else { $LayoutRoot }
    if (-not (Test-Path -LiteralPath $folder -PathType Container)) { $Problems.Add("Missing directory: $Relative"); return }
    foreach ($item in Get-ChildItem -LiteralPath $folder -Force) {
        if (-not $Relative -and -not $item.PSIsContainer -and ($item.Name -eq '.env' -or ($item.Name -like '.env.*' -and $item.Name -notlike '*.example'))) { continue }
        if (-not $Relative -and -not $item.PSIsContainer -and $item.Name -eq '.git') { continue }
        if ($item.PSIsContainer) {
            if ($item.Name -cnotin $Directories) { $Problems.Add("Unexpected directory: $Relative/$($item.Name)") }
        } elseif ($item.Name -cnotin $Files) { $Problems.Add("Unexpected file: $Relative/$($item.Name)") }
    }
    foreach ($name in $Required) {
        $path = Join-Path $folder $name
        if (-not (Test-Path -LiteralPath $path)) { $Problems.Add("Missing entry: $Relative/$name") }
        elseif ($name -cin $Directories -and -not (Test-Path -LiteralPath $path -PathType Container)) { $Problems.Add("Expected directory: $Relative/$name") }
        elseif ($name -cin $Files -and -not (Test-Path -LiteralPath $path -PathType Leaf)) { $Problems.Add("Expected file: $Relative/$name") }
    }
}
Test-Level '' $RootFiles ($RootDirectories + $GeneratedDirectories) ($RootFiles + $RootDirectories)
Test-Level 'apps' @() @('server', 'web', 'ai-service') @('server', 'web', 'ai-service')
Test-Level 'apps/server' @('pom.xml', 'mvnw', 'mvnw.cmd', 'Dockerfile', '.dockerignore') @('src', '.mvn', 'target') @('pom.xml', 'mvnw', 'mvnw.cmd', 'src', '.mvn', 'Dockerfile')
Test-Level 'apps/web' @('package.json', 'Dockerfile', 'index.html', 'eslint.config.js', 'tsconfig.json', 'vite.config.ts') @('src', 'dist', 'node_modules', 'coverage') @('package.json', 'Dockerfile', 'index.html', 'src')
Test-Level 'apps/ai-service' @('pyproject.toml', 'uv.lock', '.python-version', 'Dockerfile', '.dockerignore', '.coverage') @('src', 'tests', '.venv', '.pytest_cache', '.ruff_cache', '.mypy_cache', '__pycache__', 'dist', 'htmlcov') @('pyproject.toml', 'uv.lock', '.python-version', 'Dockerfile', 'src', 'tests')
foreach ($entry in @(
    'apps/server/src/main/java/io/testopsai/server/TestOpsApplication.java',
    'apps/server/src/main/java/io/testopsai/server/platform/security/SecurityConfiguration.java',
    'apps/server/src/test/java/io/testopsai/server/platform/security/BoundaryTest.java',
    'apps/web/src/app/main.ts', 'apps/web/src/app/App.vue',
    'apps/web/src/features/workspace/WorkspaceLayout.vue', 'apps/web/src/features/assistant/WorkspaceChatPage.vue',
    'apps/ai-service/src/testops_ai/main.py', 'apps/ai-service/src/testops_ai/app.py',
    'apps/ai-service/src/testops_ai/core/settings.py', 'apps/ai-service/src/testops_ai/features/health/router.py'
)) {
    if (-not (Test-Path -LiteralPath (Join-Path $LayoutRoot $entry) -PathType Leaf)) { $Problems.Add("Missing source entry: $entry") }
}
if ($Problems.Count -gt 0) { throw ("Layout check failed:`n" + ($Problems -join "`n") + "`nSee docs/development/DIRECTORY_LAYOUT.md.") }
Write-Output 'Layout check passed: root allowlist, required entries, application boundaries and source entrypoints.'
