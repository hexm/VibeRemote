@echo off
REM LightScript Agent 停止脚本 (Windows)

cd /d "%~dp0"

echo Stopping LightScript Agent...

setlocal enabledelayedexpansion
set STOPPED=false
set "LOCK_FILE=%USERPROFILE%\.lightscript\.agent.lock"

REM 方法1：通过锁文件读取 PID
if exist "%LOCK_FILE%" (
    for /f "tokens=2 delims=: " %%i in ('findstr "PID:" "%LOCK_FILE%" 2^>nul') do (
        for /f "tokens=1 delims=," %%j in ("%%i") do set PID=%%j
    )
    if defined PID (
        tasklist /FI "PID eq !PID!" 2>nul | find /I "!PID!" >nul
        if not errorlevel 1 (
            echo Stopping Agent process (PID: !PID!)...
            taskkill /PID !PID! /F >nul 2>&1
            timeout /t 3 /nobreak >nul
            set STOPPED=true
        )
    )
)

REM 方法2：通过 wmic 查找 agent.jar 进程
if "!STOPPED!"=="false" (
    for /f "skip=1 tokens=1" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid 2^>nul') do (
        set "WPID=%%i"
        if defined WPID (
            if "!WPID!" neq "" (
                echo Stopping Agent process (PID: !WPID!)...
                taskkill /PID !WPID! /F >nul 2>&1
                set STOPPED=true
            )
        )
    )
    if "!STOPPED!"=="true" (
        timeout /t 2 /nobreak >nul
    )
)

REM 清理锁文件
if exist "%LOCK_FILE%" (
    del /f /q "%LOCK_FILE%" >nul 2>&1
)

if "!STOPPED!"=="false" (
    echo No Agent processes found.
) else (
    echo Agent stopped. Run start-agent.bat to restart.
)

endlocal
