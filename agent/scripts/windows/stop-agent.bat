@echo off
setlocal enabledelayedexpansion

set INSTALL_DIR=%~dp0
set PROPS_FILE=%INSTALL_DIR%agent.properties
set AGENT_ID_FILE=%USERPROFILE%\.viberemote\.agent_id

echo Stopping VibeRemote Agent...

REM 读取 agent.properties
set SERVER_URL=
set AGENT_TOKEN=
if exist "%PROPS_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS_FILE%") do (
        set KEY=%%A
        set VAL=%%B
        set KEY=!KEY: =!
        if /i "!KEY!"=="server.url" set SERVER_URL=!VAL: =!
        if /i "!KEY!"=="register.token" set AGENT_TOKEN=!VAL: =!
    )
)

REM 读取 agentId
set AGENT_ID=
if exist "%AGENT_ID_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%AGENT_ID_FILE%") do (
        set KEY=%%A
        set VAL=%%B
        set KEY=!KEY: =!
        if /i "!KEY!"=="agentId" set AGENT_ID=!VAL: =!
    )
)

REM 通知服务器离线
if not "!SERVER_URL!"=="" if not "!AGENT_ID!"=="" if not "!AGENT_TOKEN!"=="" (
    echo Notifying server of offline status...
    curl -s -X POST "!SERVER_URL!/api/agent/offline" -d "agentId=!AGENT_ID!&agentToken=!AGENT_TOKEN!" >nul 2>&1
    if !errorlevel! equ 0 (
        echo   Server notified successfully.
    ) else (
        echo   Failed to notify server ^(server may be unreachable^).
    )
) else (
    echo   Skipping server notification ^(missing config^).
)

REM 优雅停止，让 JVM shutdown hook 也有机会执行
set KILLED=0
taskkill /im javaw.exe >nul 2>&1
if !errorlevel! equ 0 (echo   Sent graceful stop to javaw.exe. & set KILLED=1) else (echo   javaw.exe not running.)
taskkill /im java.exe >nul 2>&1
if !errorlevel! equ 0 (echo   Sent graceful stop to java.exe. & set KILLED=1) else (echo   java.exe not running.)

if "!KILLED!"=="1" (
    timeout /t 5 /nobreak >nul
)

REM 兜底强制终止
taskkill /f /im javaw.exe >nul 2>&1
taskkill /f /im java.exe >nul 2>&1

if "!KILLED!"=="1" (
    echo Agent stopped.
) else (
    echo Agent was not running.
)
pause
