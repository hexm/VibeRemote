@echo off
setlocal enabledelayedexpansion

set "INSTALL_DIR=%USERPROFILE%\VibeRemote-Agent"
set "VERSION=__AGENT_VERSION__"
set "BASE_DOWNLOAD_URL=http://8.138.114.34/agent/release"
set "PACKAGE_ARCH=x64"
set "DOWNLOAD_URL="
set "ZIP_FILE=%TEMP%\viberemote-agent.zip"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "INSTALL_LOG=%TEMP%\viberemote-install.log"

echo. > "%INSTALL_LOG%"

call :log "=========================================="
call :log "  VibeRemote Agent Installer v%VERSION%"
call :log "=========================================="
call :log "  Install dir: %INSTALL_DIR%"
call :log "  Install log: %INSTALL_LOG%"
call :log ""

call :detect_arch
set "DOWNLOAD_URL=%BASE_DOWNLOAD_URL%/viberemote-agent-%VERSION%-windows-%PACKAGE_ARCH%.zip"
call :log "  Detected Windows architecture: %PACKAGE_ARCH%"
call :log "  Package URL: %DOWNLOAD_URL%"
call :log ""

REM ---- 1. Kill existing agent process ----
call :log "[1/4] Stopping existing agent..."
taskkill /f /im javaw.exe >nul 2>&1
if !errorlevel! equ 0 (call :log "  javaw.exe stopped.") else (call :log "  javaw.exe not running.")
taskkill /f /im java.exe >nul 2>&1
if !errorlevel! equ 0 (call :log "  java.exe stopped.") else (call :log "  java.exe not running.")
timeout /t 2 /nobreak >nul

REM ---- 2. Download ----
call :log "[2/4] Downloading package (~44MB)..."
call :log "  URL: %DOWNLOAD_URL%"
if exist "%ZIP_FILE%" (
    call :log "  Removing old zip..."
    del /f /q "%ZIP_FILE%"
)
curl -L --fail --connect-timeout 30 --max-time 1800 -o "%ZIP_FILE%" "%DOWNLOAD_URL%"
if !errorlevel! neq 0 (
    call :log "[ERROR] Download failed."
    pause
    exit /b 1
)
call :log "  Download complete."

REM ---- 3. Clear dir and extract ----
call :log "[3/4] Clearing install directory..."
if exist "%INSTALL_DIR%" (
    call :log "  Removing old directory..."
    rmdir /s /q "%INSTALL_DIR%" >nul 2>&1
    if exist "%INSTALL_DIR%" (
        call :log "  rmdir failed, clearing files instead..."
        del /f /s /q "%INSTALL_DIR%\*" >nul 2>&1
        for /d %%i in ("%INSTALL_DIR%\*") do rmdir /s /q "%%i" >nul 2>&1
        call :log "  Files cleared."
    ) else (
        call :log "  Directory removed."
    )
)
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

call :log "  Extracting package..."
tar -xf "%ZIP_FILE%" -C "%INSTALL_DIR%" >nul 2>&1
if !errorlevel! neq 0 (
    call :log "  tar not available, trying powershell..."
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%INSTALL_DIR%' -Force"
    if !errorlevel! neq 0 (
        call :log "[ERROR] Extract failed."
        del /f /q "%ZIP_FILE%" 2>nul
        pause
        exit /b 1
    )
)
del /f /q "%ZIP_FILE%" 2>nul

if not exist "%INSTALL_DIR%\agent.jar" (
    call :log "[ERROR] agent.jar not found after extraction."
    pause
    exit /b 1
)
call :log "  Extraction complete. agent.jar verified."

REM ---- 4. Start and verify ----
call :log "[4/4] Starting agent..."
call :log "  Enabling login autostart..."
call "%INSTALL_DIR%\install-autostart.bat" --silent
if !errorlevel! equ 0 (
    call :log "  Autostart enabled."
) else (
    call :log "  [WARN] Failed to enable autostart."
)
call "%INSTALL_DIR%\start-agent.bat"
call :log "  Waiting for agent to start..."
timeout /t 5 /nobreak >nul

set RUNNING=0
tasklist /fi "imagename eq javaw.exe" 2>nul | findstr /i "javaw.exe" >nul 2>&1
if !errorlevel! equ 0 set RUNNING=1
tasklist /fi "imagename eq java.exe" 2>nul | findstr /i "java.exe" >nul 2>&1
if !errorlevel! equ 0 set RUNNING=1

if "!RUNNING!"=="1" (
    call :log "[OK] Agent is running."
) else (
    call :log "[WARN] Agent not detected. Check: %LOG_DIR%\agent.log"
)

copy /y "%INSTALL_LOG%" "%LOG_DIR%\install.log" >nul 2>&1
call :log ""
call :log "  Install log: %LOG_DIR%\install.log"
call :log "  Agent log:   %LOG_DIR%\agent.log"
call :log "=========================================="
exit /b 0

REM ---- helper: print to screen and log ----
:log
echo %~1
echo %~1 >> "%INSTALL_LOG%"
goto :eof

REM ---- helper: detect os architecture ----
:detect_arch
if /i "%PROCESSOR_ARCHITEW6432%"=="AMD64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITEW6432%"=="IA64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="AMD64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="IA64" set "PACKAGE_ARCH=x64"
goto :eof
