@echo off
echo ========================================
echo LightScript 客户端启动脚本
echo ========================================
echo.

REM 检查 Java 环境
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Java 环境，请安装 JDK 1.8 或更高版本
    pause
    exit /b 1
)

REM 检查客户端 jar 文件
if not exist "agent\target\agent-*-jar-with-dependencies.jar" (
    echo 错误: 未找到客户端 jar 文件，请先运行 mvn clean package
    pause
    exit /b 1
)

REM 设置环境变量
set /p SERVER_URL="请输入服务器地址 (默认: http://localhost:8080): "
if "%SERVER_URL%"=="" set SERVER_URL=http://localhost:8080

set /p REGISTER_TOKEN="请输入注册令牌 (默认: dev-register-token): "
if "%REGISTER_TOKEN%"=="" set REGISTER_TOKEN=dev-register-token

set LS_SERVER=%SERVER_URL%
set LS_REGISTER_TOKEN=%REGISTER_TOKEN%

echo.
echo 配置信息:
echo 服务器地址: %LS_SERVER%
echo 注册令牌: %LS_REGISTER_TOKEN%
echo.
echo 正在启动 LightScript 客户端...
echo 按 Ctrl+C 停止客户端
echo ========================================

cd agent\target
java -jar agent-*-jar-with-dependencies.jar

pause
