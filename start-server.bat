@echo off
echo ========================================
echo LightScript 服务器启动脚本
echo ========================================
echo.

REM 检查 Java 环境
java -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Java 环境，请安装 JDK 1.8 或更高版本
    pause
    exit /b 1
)

REM 检查服务器 jar 文件
if not exist "server\target\server-*.jar" (
    echo 错误: 未找到服务器 jar 文件，请先运行 mvn clean package
    pause
    exit /b 1
)

echo 正在启动 LightScript 服务器...
echo 访问地址: http://localhost:8080
echo 默认账号: admin / admin123
echo.
echo 按 Ctrl+C 停止服务器
echo ========================================

cd server\target
java -jar server-*.jar

pause
