param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArguments)
. "$PSScriptRoot/env.ps1"
Push-Location (Join-Path $ProjectRoot 'apps/server')
try {
    Invoke-Checked './mvnw.cmd' $MavenArguments
} finally { Pop-Location }
