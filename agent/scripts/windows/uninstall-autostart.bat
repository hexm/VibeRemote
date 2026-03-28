@echo off
setlocal enabledelayedexpansion

set STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set SHORTCUT_PATH=%STARTUP_DIR%\VibeRemote Agent.lnk

echo Removing VibeRemote Agent autostart...

if exist "%SHORTCUT_PATH%" (
    del /f /q "%SHORTCUT_PATH%" >nul 2>&1
)

if not exist "%SHORTCUT_PATH%" (
    echo [OK] Autostart removed.
) else (
    echo [INFO] Autostart shortcut was not set.
)

echo.
pause
