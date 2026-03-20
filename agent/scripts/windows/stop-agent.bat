@echo off
setlocal enabledelayedexpansion

echo Stopping VibeRemote Agent...

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
) else (
    echo Agent is not running.
)
pause
