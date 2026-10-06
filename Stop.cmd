@echo off
rem Windows: double-click to run.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1"  %*
echo.
pause
