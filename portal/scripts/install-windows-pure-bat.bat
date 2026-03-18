@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM LightScript Agent Windows 安装脚本
REM 支持 Windows 10 1903+ (内置 curl 和 tar)

set SERVER_URL=http://8.138.114.34:8080
set INSTALL_DIR=C:\LightScript-Agent

echo.
echo ========================================
echo   LightScript Agent Windows 安装程序
echo ========================================
echo.

REM 解析命令行参数
:parse_args
if "%~1"=="" goto :start_install
if /i "%~1"=="--server" (
    set SERVER_URL=%~2
    shift & shift
    goto :parse_args
)
if /i "%~1"=="--install-dir" (
    set INSTALL_DIR=%~2
    shift & shift
    goto :parse_args
)
if /i "%~1"=="--help" goto :show_help
shift
goto :parse_args

:show_help
echo 用法: %~nx0 [选项]
echo.
echo 选项:
echo   --server URL        服务器地址 (默认: http://8.138.114.34:8080)
echo   --install-dir DIR   安装目录   (默认: C:\LightScript-Agent)
echo   --help              显示帮助
echo.
pause
exit /b 0

:start_install
echo 服务器地址: %SERVER_URL%
echo 安装目录:   %INSTALL_DIR%
echo.

REM 检查 curl
curl --version >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] 未找到 curl，需要 Windows 10 1803 或更高版本
    echo 请手动下载: http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
    pause & exit /b 1
)

REM 检查 tar
tar --version >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] 未找到 tar，需要 Windows 10 1903 或更高版本
    echo 请手动下载并解压: http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
    pause & exit /b 1
)

echo [1/4] 创建安装目录...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if !errorlevel! neq 0 (
    echo [错误] 无法创建目录: %INSTALL_DIR%
    pause & exit /b 1
)

REM 停止已有进程并清空安装目录
echo [2/4] 检查并停止已有 Agent 进程...
for /f "skip=1 tokens=1" %%i in ('wmic process where "name='java.exe' and commandline like '%%agent.jar%%'" get processid 2^>nul') do (
    if "%%i" neq "" (
        echo 停止已有进程 (PID: %%i)...
        taskkill /PID %%i /F >nul 2>&1
    )
)
if exist "%INSTALL_DIR%" (
    echo 清空旧安装目录...
    rmdir /s /q "%INSTALL_DIR%" >nul 2>&1
)
mkdir "%INSTALL_DIR%"

echo [3/4] 下载安装包...
set ZIP_FILE=%INSTALL_DIR%\agent.zip
set DOWNLOAD_URL=http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
curl -L --fail --progress-bar --connect-timeout 30 --max-time 300 -o "%ZIP_FILE%" "%DOWNLOAD_URL%"
if !errorlevel! neq 0 (
    echo [错误] 下载失败，请检查网络或手动下载: %DOWNLOAD_URL%
    pause & exit /b 1
)

echo [4/4] 解压安装包...
tar -xf "%ZIP_FILE%" -C "%INSTALL_DIR%"
if !errorlevel! neq 0 (
    echo [错误] 解压失败
    del "%ZIP_FILE%" 2>nul
    pause & exit /b 1
)
del "%ZIP_FILE%" 2>nul

REM 验证文件
if not exist "%INSTALL_DIR%\agent.jar" (
    echo [错误] 安装包不完整，缺少 agent.jar
    pause & exit /b 1
)

REM 更新配置中的服务器地址
if exist "%INSTALL_DIR%\agent.properties" (
    powershell -Command "(Get-Content '%INSTALL_DIR%\agent.properties') -replace '^server\.url=.*', 'server.url=%SERVER_URL%' | Set-Content '%INSTALL_DIR%\agent.properties'" >nul 2>&1
)

REM 创建日志目录
if not exist "%INSTALL_DIR%\logs" mkdir "%INSTALL_DIR%\logs"

echo.
echo ========================================
echo   安装完成！
echo ========================================
echo.
echo 安装目录: %INSTALL_DIR%
echo.
echo 启动 Agent:
echo   双击 %INSTALL_DIR%\start-agent.bat
echo   或在命令行运行: cd /d "%INSTALL_DIR%" ^&^& start-agent.bat
echo.
echo 停止 Agent:
echo   运行 %INSTALL_DIR%\stop-agent.bat
echo.
echo 卸载:
echo   运行 %INSTALL_DIR%\uninstall.bat
echo.
echo 查看日志: %INSTALL_DIR%\logs\agent.log
echo.

set /p START_NOW="是否立即启动 Agent? (Y/n): "
if /i "!START_NOW!"=="n" goto :done
if /i "!START_NOW!"=="N" goto :done

echo.
echo 启动 Agent...
cd /d "%INSTALL_DIR%"
start "LightScript Agent" cmd /k start-agent.bat

:done
echo.
pause
