@echo off
chcp 65001 >nul
echo ========================================
echo LightScript Agent Startup Script
echo ========================================
echo.

REM Check Java environment
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install JDK 1.8 or higher
    pause
    exit /b 1
)

REM Check agent jar file
if not exist "agent\target\agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar" (
    echo ERROR: Agent jar file not found. Please run: mvn clean package
    pause
    exit /b 1
)

REM Set default values
set SERVER_URL=http://localhost:8080
set REGISTER_TOKEN=dev-register-token

REM Get user input for server URL
set /p INPUT_SERVER="Enter server URL (default: %SERVER_URL%): "
if not "%INPUT_SERVER%"=="" set SERVER_URL=%INPUT_SERVER%

REM Get user input for register token
set /p INPUT_TOKEN="Enter register token (default: %REGISTER_TOKEN%): "
if not "%INPUT_TOKEN%"=="" set REGISTER_TOKEN=%INPUT_TOKEN%

echo.
echo Configuration:
echo Server URL: %SERVER_URL%
echo Register Token: %REGISTER_TOKEN%
echo.
echo Starting LightScript Agent...
echo Press Ctrl+C to stop the agent
echo ========================================

REM Start the agent with parameters
cd agent\target

echo Starting agent with UTF-8 encoding...
java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar "%SERVER_URL%" "%REGISTER_TOKEN%"

echo.
echo Agent stopped.
pause
