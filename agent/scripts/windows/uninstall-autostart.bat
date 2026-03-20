@echo off
setlocal enabledelayedexpansion

set REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set REG_NAME=VibeRemoteAgent

echo Removing VibeRemote Agent autostart...

reg delete "%REG_KEY%" /v "%REG_NAME%" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo [OK] Autostart removed.
) else (
    echo [INFO] Autostart was not set.
)

echo.
pause
