@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   VibeRemote Agent Status
echo ========================================
echo.

set RUNNING=0
tasklist /fi "imagename eq javaw.exe" 2>nul | findstr /i "javaw.exe" >nul 2>&1
if !errorlevel! equ 0 set RUNNING=1
tasklist /fi "imagename eq java.exe" 2>nul | findstr /i "java.exe" >nul 2>&1
if !errorlevel! equ 0 set RUNNING=1

if "!RUNNING!"=="1" (
    echo [Status] Running
) else (
    echo [Status] Not running
)

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
