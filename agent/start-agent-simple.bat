@echo off
REM LightScript Agent 启动脚本 (Windows) - 简化版

REM 切换到脚本目录
cd /d "%~dp0"

REM 检查Agent JAR文件
if not exist "agent.jar" (
    echo ERROR: Agent JAR file not found: agent.jar
    pause
    exit /b 1
)

REM 配置Java环境
set "JAVA_CMD=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

REM 检查Java是否可用
%JAVA_CMD% -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Please install Java or set JAVA_HOME environment variable.
    pause
    exit /b 1
)

REM 启动Agent
echo Starting LightScript Agent...
echo Java Command: %JAVA_CMD%
echo Working Directory: %CD%

%JAVA_CMD% -Xmx512m -Xms128m -jar agent.jar

pause