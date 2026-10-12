$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root
$VenvPython = Join-Path $Root '.venv\Scripts\python.exe'
if (-not (Test-Path $VenvPython)) { throw 'Run START_RAKSHA360.bat first to install dependencies.' }
Write-Host 'Raksha360 local API starting on port 4000…' -ForegroundColor Cyan
& $VenvPython -m uvicorn backend.app.main:app --host 0.0.0.0 --port 4000 --reload
