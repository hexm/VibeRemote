@echo off
setlocal enabledelayedexpansion

echo Stopping VibeRemote Agent...

set KILLED=0
taskkill /f /im javaw.exe >nul 2>&1
if !errorlevel! equ 0 (
    echo   javaw.exe stopped.
    set KILLED=1
) else (
    echo   javaw.exe not running.
)
taskkill /f /im java.exe >nul 2>&1
if !errorlevel! equ 0 (
    echo   java.exe stopped.
    set KILLED=1
) else (
    echo   java.exe not running.
)

if "!KILLED!"=="1" (
    echo Agent stopped.
) else (
    echo Agent was not running.
)
pause
