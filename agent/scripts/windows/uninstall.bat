@echo off
setlocal enabledelayedexpansion

set INSTALL_DIR=%~dp0

echo Uninstalling VibeRemote Agent...
echo.

REM 读取 agent.properties
set PROPS_FILE=%INSTALL_DIR%agent.properties
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
set AGENT_ID_FILE=%USERPROFILE%\.viberemote\.agent_id
set AGENT_ID=
if exist "%AGENT_ID_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%AGENT_ID_FILE%") do (
        set KEY=%%A
        set VAL=%%B
        set KEY=!KEY: =!
        if /i "!KEY!"=="agentId" set AGENT_ID=!VAL: =!
    )
)

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

REM 通知服务器离线
if not "!SERVER_URL!"=="" if not "!AGENT_ID!"=="" if not "!AGENT_TOKEN!"=="" (
    echo   Notifying server of offline status...
    curl -s -X POST "!SERVER_URL!/api/agent/offline" -d "agentId=!AGENT_ID!&agentToken=!AGENT_TOKEN!" >nul 2>&1
    if !errorlevel! equ 0 (echo   Server notified.) else (echo   Server notification failed.)
)

set KILLED=0
taskkill /im javaw.exe >nul 2>&1
if !errorlevel! equ 0 (echo   Sent graceful stop to javaw.exe. & set KILLED=1) else (echo   javaw.exe not running.)
taskkill /im java.exe >nul 2>&1
if !errorlevel! equ 0 (echo   Sent graceful stop to java.exe. & set KILLED=1) else (echo   java.exe not running.)
if "!KILLED!"=="1" (
    timeout /t 5 /nobreak >nul
)
taskkill /f /im javaw.exe >nul 2>&1
taskkill /f /im java.exe >nul 2>&1
if "!KILLED!"=="1" (
    echo Agent stopped.
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
