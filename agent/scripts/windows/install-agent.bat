@echo off
setlocal enabledelayedexpansion

set "INSTALL_DIR=%USERPROFILE%\VibeRemote-Agent"
set "VERSION=__AGENT_VERSION__"
set "SERVER_URL=__SERVER_URL__"
set "REGISTER_TOKEN=__REGISTER_TOKEN__"
set "BASE_DOWNLOAD_URL=__PACKAGE_BASE_URL__"
set "PACKAGE_ARCH=x64"
set "DOWNLOAD_URL="
set "ZIP_FILE=%TEMP%\viberemote-agent.zip"
set "LOG_DIR=%INSTALL_DIR%\logs"
set "INSTALL_LOG=%TEMP%\viberemote-install.log"

echo. > "%INSTALL_LOG%"

call :parse_args %*
if !errorlevel! neq 0 exit /b 1
call :validate_config
if !errorlevel! neq 0 exit /b 1

call :log "=========================================="
call :log "  VibeRemote Agent Installer v%VERSION%"
call :log "=========================================="
call :log "  Install dir: %INSTALL_DIR%"
call :log "  Server URL: %SERVER_URL%"
call :log "  Register token: %REGISTER_TOKEN%"
call :log "  Package base URL: %BASE_DOWNLOAD_URL%"
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
call :download_file "%DOWNLOAD_URL%" "%ZIP_FILE%"
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
call :extract_zip "%ZIP_FILE%" "%INSTALL_DIR%"
if !errorlevel! neq 0 (
    call :log "[ERROR] Extract failed."
    del /f /q "%ZIP_FILE%" 2>nul
    pause
    exit /b 1
)
del /f /q "%ZIP_FILE%" 2>nul

if not exist "%INSTALL_DIR%\agent.jar" (
    call :log "[ERROR] agent.jar not found after extraction."
    pause
    exit /b 1
)
call :write_config
call :log "  Extraction complete. agent.jar verified."

REM ---- 4. Start and verify ----
call :log "[4/4] Starting agent..."
call :log "  Enabling login autostart..."
call "%INSTALL_DIR%\install-autostart.bat" --silent
if !errorlevel! equ 0 (
    call :log "  Autostart enabled."
) else (
    call :log "  [WARN] Built-in autostart setup failed, trying Startup folder fallback..."
    call :fallback_autostart
    if !errorlevel! equ 0 (
        call :log "  Autostart enabled via Startup folder."
    ) else (
        call :log "  [WARN] Failed to enable autostart."
    )
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

REM ---- helper: parse command-line arguments ----
:parse_args
if "%~1"=="" goto :eof

if /i "%~1"=="--help" (
    call :print_usage
    exit /b 1
)

call :log "[ERROR] Unsupported argument: %~1"
goto :usage_error

:usage_error
call :print_usage
exit /b 1

REM ---- helper: validate required settings ----
:validate_config
if "%SERVER_URL%"=="" goto :missing_server
if "%SERVER_URL:~0,2%"=="__" goto :missing_server
if "%REGISTER_TOKEN%"=="" goto :missing_token
if "%REGISTER_TOKEN:~0,2%"=="__" goto :missing_token
if "%BASE_DOWNLOAD_URL%"=="" goto :missing_package
if "%BASE_DOWNLOAD_URL:~0,2%"=="__" goto :missing_package
if "!BASE_DOWNLOAD_URL:~-1!"=="/" set "BASE_DOWNLOAD_URL=!BASE_DOWNLOAD_URL:~0,-1!"
goto :eof

:missing_server
call :log "[ERROR] Installer is missing SERVER_URL. Rebuild the installer for the current environment."
call :print_usage
exit /b 1

:missing_token
call :log "[ERROR] Installer is missing REGISTER_TOKEN. Rebuild the installer for the current environment."
call :print_usage
exit /b 1

:missing_package
call :log "[ERROR] Installer is missing PACKAGE_BASE_URL. Rebuild the installer for the current environment."
call :print_usage
exit /b 1

REM ---- helper: rewrite agent.properties ----
:write_config
set "CONFIG_FILE=%INSTALL_DIR%\agent.properties"
set "TEMP_CONFIG=%TEMP%\viberemote-agent.properties.tmp"

> "%TEMP_CONFIG%" (
    echo server.url=%SERVER_URL%
    echo register.token=%REGISTER_TOKEN%
)

if exist "%CONFIG_FILE%" (
    for /f "usebackq delims=" %%L in ("%CONFIG_FILE%") do (
        set "LINE=%%L"
        if /i not "!LINE:~0,11!"=="server.url=" if /i not "!LINE:~0,15!"=="register.token=" >> "%TEMP_CONFIG%" echo(!LINE!
    )
)

move /y "%TEMP_CONFIG%" "%CONFIG_FILE%" >nul
call :log "  Updated agent.properties with selected server settings."
goto :eof

REM ---- helper: download file with fallbacks for old Windows ----
:download_file
set "DL_URL=%~1"
set "DL_TARGET=%~2"

where curl >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Download method: curl"
    curl -L --fail --connect-timeout 30 --max-time 1800 -o "%DL_TARGET%" "%DL_URL%"
    if !errorlevel! equ 0 if exist "%DL_TARGET%" exit /b 0
    call :log "  curl failed, trying next method..."
)

where powershell >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Download method: PowerShell Invoke-WebRequest"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri '%DL_URL%' -OutFile '%DL_TARGET%' -UseBasicParsing } catch { exit 1 }"
    if !errorlevel! equ 0 if exist "%DL_TARGET%" exit /b 0
    call :log "  PowerShell download failed, trying next method..."
)

where bitsadmin >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Download method: BITSAdmin"
    bitsadmin /transfer "VibeRemoteDownload" /download /priority normal "%DL_URL%" "%DL_TARGET%" >nul 2>&1
    if !errorlevel! equ 0 if exist "%DL_TARGET%" exit /b 0
    call :log "  BITSAdmin download failed, trying next method..."
)

where certutil >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Download method: certutil"
    certutil -urlcache -split -f "%DL_URL%" "%DL_TARGET%" >nul 2>&1
    if !errorlevel! equ 0 if exist "%DL_TARGET%" exit /b 0
    call :log "  certutil download failed."
)

exit /b 1

REM ---- helper: extract zip with Win7-compatible fallbacks ----
:extract_zip
set "EZ_ZIP=%~1"
set "EZ_DEST=%~2"

where tar >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Extract method: tar"
    tar -xf "%EZ_ZIP%" -C "%EZ_DEST%" >nul 2>&1
    if !errorlevel! equ 0 exit /b 0
    call :log "  tar extract failed, trying next method..."
)

where powershell >nul 2>&1
if !errorlevel! equ 0 (
    call :log "  Extract method: PowerShell Expand-Archive"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Expand-Archive -LiteralPath '%EZ_ZIP%' -DestinationPath '%EZ_DEST%' -Force } catch { exit 1 }"
    if !errorlevel! equ 0 exit /b 0
    call :log "  PowerShell extract failed, trying next method..."
)

call :log "  Extract method: VBScript Shell.Application"
set "VBS_EXTRACT=%TEMP%\viberemote-unzip.vbs"
> "%VBS_EXTRACT%" echo Set fso = CreateObject("Scripting.FileSystemObject")
>> "%VBS_EXTRACT%" echo zipPath = WScript.Arguments(0)
>> "%VBS_EXTRACT%" echo destPath = WScript.Arguments(1)
>> "%VBS_EXTRACT%" echo If Not fso.FolderExists(destPath) Then fso.CreateFolder(destPath)
>> "%VBS_EXTRACT%" echo Set sh = CreateObject("Shell.Application")
>> "%VBS_EXTRACT%" echo Set src = sh.NameSpace(zipPath)
>> "%VBS_EXTRACT%" echo Set dst = sh.NameSpace(destPath)
>> "%VBS_EXTRACT%" echo If src Is Nothing Or dst Is Nothing Then WScript.Quit 1
>> "%VBS_EXTRACT%" echo dst.CopyHere src.Items, 16 + 1024
>> "%VBS_EXTRACT%" echo WScript.Sleep 8000
wscript //nologo "%VBS_EXTRACT%" "%EZ_ZIP%" "%EZ_DEST%" >nul 2>&1
set "VBS_RC=!errorlevel!"
del /f /q "%VBS_EXTRACT%" >nul 2>&1
if "!VBS_RC!"=="0" exit /b 0

exit /b 1

REM ---- helper: autostart fallback for old Windows ----
:fallback_autostart
set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "STARTUP_CMD=%STARTUP_DIR%\VibeRemote Agent.cmd"
if not exist "%STARTUP_DIR%" mkdir "%STARTUP_DIR%" >nul 2>&1
> "%STARTUP_CMD%" echo @echo off
>> "%STARTUP_CMD%" echo cd /d "%INSTALL_DIR%"
>> "%STARTUP_CMD%" echo call "%INSTALL_DIR%\start-agent.bat"
if exist "%STARTUP_CMD%" exit /b 0
exit /b 1

REM ---- helper: print to screen and log ----
:log
if "%~1"=="" (
    echo.
    echo.>> "%INSTALL_LOG%"
    goto :eof
)
echo %~1
echo %~1 >> "%INSTALL_LOG%"
goto :eof

REM ---- helper: usage ----
:print_usage
call :log "Usage:"
call :log "  install-agent.bat"
call :log ""
call :log "This installer is generated for a specific environment."
call :log "If server info is missing, republish the portal/install script."
goto :eof

REM ---- helper: detect os architecture ----
:detect_arch
if /i "%PROCESSOR_ARCHITEW6432%"=="AMD64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITEW6432%"=="IA64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="AMD64" set "PACKAGE_ARCH=x64"
if /i "%PROCESSOR_ARCHITECTURE%"=="IA64" set "PACKAGE_ARCH=x64"
goto :eof
