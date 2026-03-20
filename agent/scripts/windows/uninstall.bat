@echo off
setlocal enabledelayedexpansion

set INSTALL_DIR=%~dp0

echo Uninstalling VibeRemote Agent...
echo.

REM Step 1: Remove autostart registry entry
echo [1/3] Removing autostart...
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "VibeRemoteAgent" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo Autostart removed.
) else (
    echo Autostart not set, skipping.
)

REM Step 2: Stop process
echo [2/3] Stopping agent process...
set KILLED=0
taskkill /f /im javaw.exe >nul 2>&1
if !errorlevel! equ 0 (echo   javaw.exe stopped. & set KILLED=1) else (echo   javaw.exe not running.)
taskkill /f /im java.exe >nul 2>&1
if !errorlevel! equ 0 (echo   java.exe stopped. & set KILLED=1) else (echo   java.exe not running.)
if "!KILLED!"=="1" (
    echo Agent stopped.
    timeout /t 2 /nobreak >nul
) else (
    echo Agent was not running.
)

REM Step 3: Delete files
echo [3/3] Deleting files...
if exist "%USERPROFILE%\.viberemote\.agent_id" del /f /q "%USERPROFILE%\.viberemote\.agent_id" >nul 2>&1
cd /d "%TEMP%"
start "" cmd /c "timeout /t 1 /nobreak >nul & rd /s /q ""%INSTALL_DIR%"""

echo.
echo VibeRemote Agent uninstalled.
exit /b 0
