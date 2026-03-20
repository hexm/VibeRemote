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
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq javaw.exe" /fo csv /nh 2^>nul') do (
    set PID=%%~i
    wmic process where "processid='!PID!'" get commandline 2>nul | findstr /i "agent.jar" >nul 2>&1
    if !errorlevel! equ 0 (
        taskkill /PID !PID! /F >nul 2>&1
        set KILLED=1
    )
)
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq java.exe" /fo csv /nh 2^>nul') do (
    set PID=%%~i
    wmic process where "processid='!PID!'" get commandline 2>nul | findstr /i "agent.jar" >nul 2>&1
    if !errorlevel! equ 0 (
        taskkill /PID !PID! /F >nul 2>&1
        set KILLED=1
    )
)
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
