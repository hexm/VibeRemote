@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set REG_KEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set REG_NAME=VibeRemoteAgent

echo Setting up VibeRemote Agent autostart...

reg add "%REG_KEY%" /v "%REG_NAME%" /t REG_SZ /d "\"%SCRIPT_DIR%start-agent.bat\"" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo [OK] Autostart enabled.
    echo Path: %SCRIPT_DIR%start-agent.bat
) else (
    echo [FAIL] Failed to set autostart.
)

echo.
pause
