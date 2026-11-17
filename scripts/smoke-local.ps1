. "$PSScriptRoot/env.ps1"
Invoke-Checked uv @('run', '--project', "$ProjectRoot/apps/ai-service", '--locked', 'python', "$PSScriptRoot/smoke-local.py")
