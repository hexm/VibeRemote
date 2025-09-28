@echo off
chcp 65001 >nul
echo ========================================
echo LightScript Agent Startup Script
echo ========================================
echo.

set SERVER_URL=http://localhost:8080
set REGISTER_TOKEN=dev-register-token

if not "%1"=="" set SERVER_URL=%1
if not "%2"=="" set REGISTER_TOKEN=%2

echo Configuration:
echo - Server URL: %SERVER_URL%
echo - Register Token: %REGISTER_TOKEN%
echo.

echo Building Agent...
call mvn clean package -DskipTests -q

if errorlevel 1 (
    echo Build failed! Please check error messages.
    pause
    exit /b 1
)

echo.
echo Build successful! Starting Agent...
echo.
echo Log files location:
echo - Main log: logs/agent.log
echo - Task log: logs/tasks.log
echo.
echo Press Ctrl+C to stop Agent
echo ========================================

if not exist logs mkdir logs

java -jar target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar %SERVER_URL% %REGISTER_TOKEN%

pause
