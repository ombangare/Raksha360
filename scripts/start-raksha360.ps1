$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

if (-not (Test-Path (Join-Path $Root '.env'))) {
    $Template = Get-Content (Join-Path $Root '.env.example') -Raw
    $DeviceToken = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    $DashboardToken = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    $Template = $Template -replace '(?m)^RAKSHA360_DEVICE_TOKEN=.*$', "RAKSHA360_DEVICE_TOKEN=$DeviceToken"
    $Template = $Template -replace '(?m)^RAKSHA360_DASHBOARD_TOKEN=.*$', "RAKSHA360_DASHBOARD_TOKEN=$DashboardToken"
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $Root '.env'), $Template, $Utf8NoBom)
    Write-Host 'Created .env with fresh random development tokens. Keep this file private.' -ForegroundColor Yellow
}

$Python = Get-Command python -ErrorAction SilentlyContinue
if (-not $Python) {
    throw 'Python was not found. Install Python 3.11 or newer and enable Add Python to PATH.'
}
$Node = Get-Command node -ErrorAction SilentlyContinue
$Npm = Get-Command npm -ErrorAction SilentlyContinue
if (-not $Node -or -not $Npm) {
    throw 'Node.js/npm were not found. Install a current Node.js LTS release, then run this launcher again.'
}

$VenvPython = Join-Path $Root '.venv\Scripts\python.exe'
if (-not (Test-Path $VenvPython)) {
    Write-Host 'Creating backend virtual environment…' -ForegroundColor Cyan
    & $Python.Source -m venv (Join-Path $Root '.venv')
}
if (-not (Test-Path $VenvPython)) { throw 'Could not create the Python virtual environment.' }
Write-Host 'Installing/updating backend dependencies…' -ForegroundColor Cyan
& $VenvPython -m pip install --upgrade pip
& $VenvPython -m pip install -r (Join-Path $Root 'backend\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Backend dependency installation failed. Check the backend terminal for details.' }

$RunBackend = Join-Path $Root 'scripts\run-backend.ps1'
$RunDashboard = Join-Path $Root 'scripts\run-dashboard.ps1'
Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoExit','-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$RunBackend`"") -WorkingDirectory $Root
Start-Sleep -Seconds 2
Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoExit','-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$RunDashboard`"") -WorkingDirectory $Root

$LanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.AddressState -eq 'Preferred' } |
    Sort-Object { if ($_.InterfaceAlias -match 'Wi-?Fi|Wireless|Ethernet') { 0 } else { 1 } } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host ''
Write-Host 'Raksha360 startup requested.' -ForegroundColor Green
Write-Host 'Police dashboard: http://localhost:5173'
Write-Host 'Backend health:   http://127.0.0.1:4000/api/v1/health'
Write-Host 'API documentation:http://127.0.0.1:4000/docs'
if ($LanIp) {
    Write-Host "Android physical phone base URL: http://${LanIp}:4000/api/v1" -ForegroundColor Yellow
    Write-Host 'Keep the phone and laptop on the same trusted Wi-Fi. Allow TCP 4000 through Windows Firewall on Private networks if needed.'
}
Write-Host 'Dashboard token and device token are in the root .env file.'
Write-Host 'Do not expose this development server to the public internet.'
