@echo off
echo ========================================
echo LightScript 项目构建脚本
echo ========================================
echo.

REM 检查 Maven 环境
mvn -version >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Maven 环境，请安装 Apache Maven
    pause
    exit /b 1
)

echo 正在清理项目...
mvn clean

echo.
echo 正在编译和打包项目...
mvn package -DskipTests

if errorlevel 1 (
    echo.
    echo 构建失败！请检查错误信息。
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建完成！
echo ========================================
echo.
echo 生成的文件:
echo - 服务器: server\target\server-*.jar
echo - 客户端: agent\target\agent-*-jar-with-dependencies.jar
echo.
echo 接下来可以运行:
echo - start-server.bat (启动服务器)
echo - start-agent.bat (启动客户端)
echo.

pause
