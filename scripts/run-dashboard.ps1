$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Dashboard = Join-Path $Root 'police-dashboard'
Set-Location $Dashboard
if (-not (Test-Path (Join-Path $Dashboard 'node_modules'))) {
    Write-Host 'Installing dashboard dependencies (first start may take a few minutes)…' -ForegroundColor Cyan
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed. Check your internet connection and rerun START_RAKSHA360.bat.' }
}
Write-Host 'Police Control Room starting on port 5173…' -ForegroundColor Cyan
Write-Host 'Open http://localhost:5173 in your browser.' -ForegroundColor Green
npm run dev
