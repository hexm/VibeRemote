@echo off
REM LightScript Agent 启动脚本 (Windows)

REM 切换到脚本目录
cd /d "%~dp0"

REM 配置Java环境
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    where java >nul 2>&1
    if %errorlevel% equ 0 (
        set "JAVA_CMD=java"
    ) else (
        echo ERROR: Java not found. Please install Java or set JAVA_HOME environment variable.
        exit /b 1
    )
)

REM Agent配置
set "AGENT_JAR=agent.jar"

REM JVM参数配置
if not defined LIGHTSCRIPT_JVM_OPTS (
    set "JVM_OPTS=-Xmx512m -Xms128m"
) else (
    set "JVM_OPTS=%LIGHTSCRIPT_JVM_OPTS%"
)

REM 检查Agent JAR文件
if not exist "%AGENT_JAR%" (
    echo ERROR: Agent JAR file not found: %AGENT_JAR%
    exit /b 1
)

REM 启动Agent
echo Starting LightScript Agent...
echo Java Command: %JAVA_CMD%
echo JVM Options: %JVM_OPTS%
echo Working Directory: %CD%

"%JAVA_CMD%" %JVM_OPTS% -jar "%AGENT_JAR%"