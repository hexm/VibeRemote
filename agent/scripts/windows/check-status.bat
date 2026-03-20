@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   VibeRemote Agent Status
echo ========================================
echo.

set RUNNING=0
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq javaw.exe" /fo csv /nh 2^>nul') do (
    set PID=%%~i
    wmic process where "processid='!PID!'" get commandline 2>nul | findstr /i "agent.jar" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [Status] Running (PID: !PID!)
        set RUNNING=1
    )
)
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq java.exe" /fo csv /nh 2^>nul') do (
    set PID=%%~i
    wmic process where "processid='!PID!'" get commandline 2>nul | findstr /i "agent.jar" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [Status] Running (PID: !PID!)
        set RUNNING=1
    )
)
if "!RUNNING!"=="0" echo [Status] Not running

echo.

set LOG_FILE=%~dp0logs\agent.log
if exist "%LOG_FILE%" (
    echo [Last 10 log lines]
    echo ----------------------------------------
    powershell -Command "Get-Content '%LOG_FILE%' -Tail 10" 2>nul
) else (
    echo [Log] No log file found
)

echo.
pause
