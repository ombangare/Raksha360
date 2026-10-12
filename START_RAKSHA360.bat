@echo off
setlocal
set "ROOT=%~dp0"
cd /d "%ROOT%"
rem The PowerShell launcher creates .env with fresh random tokens on first run.
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\start-raksha360.ps1"
if errorlevel 1 echo Raksha360 did not start successfully. Check the PowerShell error above.
echo.
pause
endlocal
