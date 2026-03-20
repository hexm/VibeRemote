@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist "logs" mkdir "logs"

if not exist "agent.jar" (
    echo [ERROR] agent.jar not found
    pause
    exit /b 1
)

REM Use javaw.exe (no console window) so this window can close
if exist "jre\bin\javaw.exe" (
    set "JAVA_CMD=%~dp0jre\bin\javaw.exe"
) else if exist "jre\bin\java.exe" (
    set "JAVA_CMD=%~dp0jre\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
) else (
    set "JAVA_CMD=javaw"
)

REM javaw runs detached from console; logback writes to file via -Dlog.home
echo Starting VibeRemote Agent...
echo Log: %~dp0logs\agent.log
start "" "%JAVA_CMD%" -Xmx512m -Xms128m -Dfile.encoding=UTF-8 "-Dlog.home=%~dp0logs" -jar "%~dp0agent.jar"
echo Agent started. This window will close.
timeout /t 2 /nobreak >nul
