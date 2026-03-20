@echo off
setlocal enabledelayedexpansion

set "INSTALL_DIR=%USERPROFILE%\VibeRemote-Agent"
set "VERSION=0.4.0"
set "DOWNLOAD_URL=http://8.138.114.34/agent/release/viberemote-agent-%VERSION%-windows-x64.zip"
set "ZIP_FILE=%TEMP%\viberemote-agent.zip"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "INSTALL_LOG=%TEMP%\viberemote-install.log"

REM Use TEMP for install log since INSTALL_DIR may be deleted during install
echo. > "%INSTALL_LOG%"
echo ========================================== >> "%INSTALL_LOG%"
echo   VibeRemote Agent Installer v%VERSION% >> "%INSTALL_LOG%"
echo ========================================== >> "%INSTALL_LOG%"
echo Install dir: %INSTALL_DIR% >> "%INSTALL_LOG%"
echo.
echo ==========================================
echo   VibeRemote Agent Installer v%VERSION%
echo ==========================================
echo Install dir: %INSTALL_DIR%
echo Install log: %INSTALL_LOG%
echo.

REM ---- 1. Kill existing agent process ----
echo [1/4] Stopping existing agent...
echo [1/4] Stopping existing agent... >> "%INSTALL_LOG%"
taskkill /f /im javaw.exe >> "%INSTALL_LOG%" 2>&1
if !errorlevel! equ 0 (echo   javaw.exe stopped.) else (echo   javaw.exe not running.)
taskkill /f /im java.exe >> "%INSTALL_LOG%" 2>&1
if !errorlevel! equ 0 (echo   java.exe stopped.) else (echo   java.exe not running.)
timeout /t 2 /nobreak >nul

REM ---- 2. Download ----
echo [2/4] Downloading package (~44MB)...
echo [2/4] Downloading... >> "%INSTALL_LOG%"
echo   URL: %DOWNLOAD_URL% >> "%INSTALL_LOG%"
if exist "%ZIP_FILE%" del /f /q "%ZIP_FILE%"
curl -L --fail --connect-timeout 30 --max-time 1800 -o "%ZIP_FILE%" "%DOWNLOAD_URL%"
if !errorlevel! neq 0 (
    echo [ERROR] Download failed. >> "%INSTALL_LOG%"
    echo [ERROR] Download failed.
    pause
    exit /b 1
)
echo   Download complete. >> "%INSTALL_LOG%"

REM ---- 3. Clear dir and extract ----
echo [3/4] Clearing and extracting...
echo [3/4] Clearing and extracting... >> "%INSTALL_LOG%"
if exist "%INSTALL_DIR%" (
    echo   Trying rmdir... >> "%INSTALL_LOG%"
    rmdir /s /q "%INSTALL_DIR%" 2>>"%INSTALL_LOG%"
    if exist "%INSTALL_DIR%" (
        echo   rmdir failed, clearing files... >> "%INSTALL_LOG%"
        del /f /s /q "%INSTALL_DIR%\*" >nul 2>&1
        for /d %%i in ("%INSTALL_DIR%\*") do rmdir /s /q "%%i" >nul 2>&1
        echo   Files cleared. >> "%INSTALL_LOG%"
    ) else (
        echo   Directory removed. >> "%INSTALL_LOG%"
    )
)
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

echo   Extracting zip... >> "%INSTALL_LOG%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%ZIP_FILE%', '%INSTALL_DIR%')"
if !errorlevel! neq 0 (
    echo [ERROR] Extract failed. >> "%INSTALL_LOG%"
    echo [ERROR] Extract failed.
    del /f /q "%ZIP_FILE%" 2>nul
    pause
    exit /b 1
)
del /f /q "%ZIP_FILE%" 2>nul
echo   Extraction complete. >> "%INSTALL_LOG%"

if not exist "%INSTALL_DIR%\agent.jar" (
    echo [ERROR] agent.jar not found. >> "%INSTALL_LOG%"
    echo [ERROR] agent.jar not found after extraction.
    pause
    exit /b 1
)
echo   agent.jar verified. >> "%INSTALL_LOG%"

REM Recreate log dir after extraction (may have been wiped)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM Copy install log into agent log dir
copy /y "%INSTALL_LOG%" "%LOG_DIR%\install.log" >nul 2>&1

REM ---- 4. Start and verify ----
echo [4/4] Starting agent...
echo [4/4] Starting agent... >> "%INSTALL_LOG%"
call "%INSTALL_DIR%\start-agent.bat" >> "%INSTALL_LOG%" 2>&1
timeout /t 5 /nobreak >nul

tasklist /fi "imagename eq javaw.exe" 2>nul | findstr /i "javaw.exe" >nul 2>&1
if !errorlevel! equ 0 (
    echo [OK] Agent is running. >> "%INSTALL_LOG%"
    echo [OK] Agent is running.
) else (
    tasklist /fi "imagename eq java.exe" 2>nul | findstr /i "java.exe" >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK] Agent is running. >> "%INSTALL_LOG%"
        echo [OK] Agent is running.
    ) else (
        echo [WARN] Agent not detected. >> "%INSTALL_LOG%"
        echo [WARN] Agent not detected. Check: %LOG_DIR%\agent.log
    )
)

echo ========================================== >> "%INSTALL_LOG%"
echo   Installation complete. >> "%INSTALL_LOG%"
echo ========================================== >> "%INSTALL_LOG%"
copy /y "%INSTALL_LOG%" "%LOG_DIR%\install.log" >nul 2>&1
echo.
echo Installation complete.
echo Install log: %LOG_DIR%\install.log
echo Agent log:   %LOG_DIR%\agent.log
echo.
exit /b 0
