@echo off
chcp 65001 >nul
echo ========================================
echo LightScript Agent - Quick Start
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

if not exist logs mkdir logs

echo Starting Agent...
echo Press Ctrl+C to stop Agent
echo ========================================

java -jar target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar %SERVER_URL% %REGISTER_TOKEN%

pause
