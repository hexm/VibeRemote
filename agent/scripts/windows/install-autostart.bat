@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set SHORTCUT_PATH=%STARTUP_DIR%\VibeRemote Agent.lnk
set SILENT_MODE=

if /i "%~1"=="--silent" set SILENT_MODE=1

echo Setting up VibeRemote Agent autostart...

if not exist "%STARTUP_DIR%" mkdir "%STARTUP_DIR%" >nul 2>&1

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $sc = $ws.CreateShortcut('%SHORTCUT_PATH%'); $sc.TargetPath = '%SCRIPT_DIR%start-agent.bat'; $sc.WorkingDirectory = '%SCRIPT_DIR%'; $sc.IconLocation = '%SystemRoot%\System32\shell32.dll,2'; $sc.Save()"
if !errorlevel! equ 0 if exist "%SHORTCUT_PATH%" (
    echo [OK] Autostart enabled.
    echo Shortcut: %SHORTCUT_PATH%
) else (
    echo [FAIL] Failed to set autostart.
    if defined SILENT_MODE exit /b 1
    echo.
    pause
    exit /b 1
)

echo.
if not defined SILENT_MODE pause
exit /b 0
