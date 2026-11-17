. "$PSScriptRoot/env.ps1"
foreach ($tool in @('java', 'node', 'npm', 'uv')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Missing $tool. See README.md for tool prerequisites; env.ps1 does not install SDKs."
    }
}
$ExpectedNode = (Get-Content -LiteralPath (Join-Path $ProjectRoot '.nvmrc') -Raw).Trim().TrimStart('v')
$Package = Get-Content -LiteralPath (Join-Path $ProjectRoot 'package.json') -Raw | ConvertFrom-Json
$ExpectedNpm = $Package.packageManager -replace '^npm@', ''
$NodeVersion = (& node -p 'process.versions.node').Trim()
$NpmVersion = (& npm --version).Trim()
$JavaVersion = (& java --version | Select-Object -First 1)
if ($NodeVersion -ne $ExpectedNode) { throw "Node $ExpectedNode required; found $NodeVersion" }
if ($NpmVersion -ne $ExpectedNpm) { throw "npm $ExpectedNpm required; found $NpmVersion" }
if ($JavaVersion -notmatch '^(openjdk|java) 21\.') { throw "JDK 21 required; found $JavaVersion" }
Invoke-Checked uv @('python', 'find', '3.12')
Write-Output "Tool prerequisites passed: Node $NodeVersion, npm $NpmVersion, JDK 21, Python 3.12, uv."
