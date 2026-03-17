@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM LightScript Agent Windows 纯批处理安装脚本
REM 不依赖PowerShell，使用curl和tar进行下载和解压

set SERVER_URL=http://8.138.114.34:8080
set INSTALL_DIR=C:\Program Files\LightScript
set SERVICE_NAME=LightScriptAgent
set MANUAL_MODE=

echo.
echo ========================================
echo   LightScript Agent Windows 安装程序
echo ========================================
echo.

REM 解析命令行参数
:parse_args
if "%1"=="" goto :start_install
if "%1"=="--server" (
    set SERVER_URL=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--install-dir" (
    set INSTALL_DIR=%2
    shift
    shift
    goto :parse_args
)
if "%1"=="--manual" (
    set MANUAL_MODE=1
    shift
    goto :parse_args
)
if "%1"=="--help" (
    goto :show_help
)
shift
goto :parse_args

:show_help
echo 用法: %0 [选项]
echo.
echo 选项:
echo   --server URL        指定服务器地址 (默认: http://8.138.114.34:8080)
echo   --install-dir DIR   指定安装目录 (默认: C:\Program Files\LightScript)
echo   --manual            手动模式，不安装Windows服务
echo   --help              显示此帮助信息
echo.
echo 示例:
echo   %0                                    # 默认安装
echo   %0 --server http://your-server:8080  # 指定服务器
echo   %0 --manual                          # 手动模式
echo.
pause
exit /b 0

:start_install
echo 🚀 开始安装 LightScript Agent...
echo 📡 服务器地址: %SERVER_URL%
echo 📁 安装目录: %INSTALL_DIR%

if defined MANUAL_MODE (
    echo 📦 安装模式: 手动模式（不安装Windows服务）
) else (
    echo 📦 安装模式: 服务模式（自动安装Windows服务）
)
echo.

REM 检查管理员权限（仅在服务模式需要）
if not defined MANUAL_MODE (
    net session >nul 2>&1
    if !errorlevel! neq 0 (
        echo ❌ 服务模式需要管理员权限
        echo.
        echo 请选择以下方式之一:
        echo   1. 以管理员身份重新运行此脚本
        echo   2. 使用手动模式: %0 --manual
        echo.
        pause
        exit /b 1
    )
    echo ✅ 管理员权限检查通过
)

REM 检查必要工具
echo 🔍 检查系统工具...

REM 检查curl
curl --version >nul 2>&1
if !errorlevel! neq 0 (
    echo ❌ 未找到curl工具
    echo.
    echo curl是Windows 10 1803+和Windows 11的内置工具
    echo 如果您使用较旧版本的Windows，请:
    echo   1. 升级到Windows 10 1803或更高版本
    echo   2. 或手动下载安装包: http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
    echo.
    pause
    exit /b 1
)
echo ✅ curl工具可用

REM 检查tar（Windows 10 1903+内置）
tar --version >nul 2>&1
if !errorlevel! neq 0 (
    echo ❌ 未找到tar工具
    echo.
    echo tar是Windows 10 1903+的内置工具
    echo 如果您使用较旧版本的Windows，请:
    echo   1. 升级到Windows 10 1903或更高版本
    echo   2. 或手动下载安装包并解压: http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
    echo.
    pause
    exit /b 1
)
echo ✅ tar工具可用

echo.

REM 创建安装目录
echo 📁 创建安装目录...
if not exist "%INSTALL_DIR%" (
    mkdir "%INSTALL_DIR%" 2>nul
    if !errorlevel! neq 0 (
        echo ❌ 无法创建安装目录: %INSTALL_DIR%
        echo 请检查权限或选择其他目录
        pause
        exit /b 1
    )
)
echo ✅ 安装目录已准备: %INSTALL_DIR%

REM 切换到安装目录
cd /d "%INSTALL_DIR%"
if !errorlevel! neq 0 (
    echo ❌ 无法访问安装目录: %INSTALL_DIR%
    pause
    exit /b 1
)

REM 停止现有服务（如果存在）
if not defined MANUAL_MODE (
    sc query "%SERVICE_NAME%" >nul 2>&1
    if !errorlevel! equ 0 (
        echo 🛑 停止现有服务...
        net stop "%SERVICE_NAME%" >nul 2>&1
        timeout /t 3 /nobreak >nul
    )
)

REM 下载安装包
echo 📥 下载安装包...
set DOWNLOAD_URL=http://8.138.114.34/agent/release/lightscript-agent-0.4.0-windows-x64.zip
set ZIP_FILE=%INSTALL_DIR%\lightscript-agent.zip

echo 正在从 %DOWNLOAD_URL% 下载...
curl -L -o "%ZIP_FILE%" "%DOWNLOAD_URL%" --progress-bar --fail --connect-timeout 30 --max-time 300
if !errorlevel! neq 0 (
    echo ❌ 下载失败
    echo.
    echo 可能的原因:
    echo   1. 网络连接问题
    echo   2. 服务器不可访问
    echo   3. 文件不存在
    echo.
    echo 请检查网络连接或手动下载: %DOWNLOAD_URL%
    pause
    exit /b 1
)

REM 检查下载的文件大小
for %%A in ("%ZIP_FILE%") do set FILE_SIZE=%%~zA
if !FILE_SIZE! LSS 1000000 (
    echo ❌ 下载的文件太小 (!FILE_SIZE! 字节)，可能下载不完整
    del "%ZIP_FILE%" 2>nul
    pause
    exit /b 1
)

set /a FILE_SIZE_MB=!FILE_SIZE!/1024/1024
echo ✅ 下载完成 (!FILE_SIZE_MB! MB)

REM 备份现有安装（如果存在）
if exist "agent.jar" (
    echo 💾 备份现有安装...
    set BACKUP_DIR=%INSTALL_DIR%\backup-%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%
    set BACKUP_DIR=!BACKUP_DIR: =0!
    mkdir "!BACKUP_DIR!" 2>nul
    move agent.jar "!BACKUP_DIR!\" >nul 2>&1
    move *.bat "!BACKUP_DIR!\" >nul 2>&1
    move logs "!BACKUP_DIR!\" >nul 2>&1
    echo ✅ 现有安装已备份到: !BACKUP_DIR!
)

REM 解压安装包
echo 📂 解压安装包...
tar -xf "%ZIP_FILE%" 2>nul
if !errorlevel! neq 0 (
    echo ❌ 解压失败
    echo.
    echo 可能的原因:
    echo   1. ZIP文件损坏
    echo   2. 磁盘空间不足
    echo   3. 权限不足
    echo.
    pause
    exit /b 1
)

REM 清理下载的ZIP文件
del "%ZIP_FILE%" 2>nul
echo ✅ 解压完成

REM 验证关键文件
if not exist "agent.jar" (
    echo ❌ 安装包不完整，缺少 agent.jar
    pause
    exit /b 1
)

if not exist "start-agent.bat" (
    echo ❌ 安装包不完整，缺少 start-agent.bat
    pause
    exit /b 1
)

echo ✅ 安装文件验证通过

REM 配置服务器地址
echo ⚙️  配置服务器地址...
if exist "agent.properties" (
    REM 更新现有配置文件中的服务器地址
    powershell -Command "(Get-Content 'agent.properties') -replace '^server\.url=.*', 'server.url=%SERVER_URL%' | Set-Content 'agent.properties'" 2>nul
    if !errorlevel! neq 0 (
        REM 如果PowerShell不可用，使用简单的echo覆盖
        echo server.url=%SERVER_URL% > temp_server.txt
        type agent.properties | findstr /v "^server.url=" > temp_config.txt
        copy temp_server.txt + temp_config.txt agent.properties >nul
        del temp_server.txt temp_config.txt 2>nul
    )
) else (
    REM 创建基本配置文件
    echo server.url=%SERVER_URL% > agent.properties
    echo server.register.token=dev-register-token >> agent.properties
)
echo ✅ 服务器地址已配置: %SERVER_URL%

REM 创建日志目录
if not exist "logs" mkdir "logs"

if defined MANUAL_MODE (
    REM 手动模式安装完成
    echo.
    echo ========================================
    echo ✅ LightScript Agent 安装完成!
    echo ========================================
    echo.
    echo 📊 手动启动: start-agent.bat
    echo 🛑 手动停止: stop-agent.bat
    echo 📋 查看日志: type logs\agent.log
    echo 📁 安装目录: %INSTALL_DIR%
    echo 🌐 管理后台: %SERVER_URL%
    echo.
    echo 💡 提示:
    echo   - 首次启动可能需要几秒钟来注册Agent
    echo   - 如需开机自启，请以管理员身份重新运行安装程序（不使用--manual参数）
    echo.
) else (
    REM 服务模式
    echo 🔧 安装 Windows 服务...
    
    REM 删除现有服务（如果存在）
    sc query "%SERVICE_NAME%" >nul 2>&1
    if !errorlevel! equ 0 (
        echo 删除现有服务...
        sc delete "%SERVICE_NAME%" >nul 2>&1
        timeout /t 2 /nobreak >nul
    )
    
    REM 创建新服务
    sc create "%SERVICE_NAME%" binPath= "cmd.exe /c \"%INSTALL_DIR%\start-agent.bat\" --service" DisplayName= "LightScript Agent" start= auto depend= Tcpip >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✅ 服务创建成功
        
        REM 设置服务描述和恢复选项
        sc description "%SERVICE_NAME%" "LightScript 分布式脚本执行代理" >nul 2>&1
        sc failure "%SERVICE_NAME%" reset= 86400 actions= restart/30000/restart/60000/restart/120000 >nul 2>&1
        
        REM 启动服务
        echo 🚀 启动服务...
        net start "%SERVICE_NAME%" >nul 2>&1
        if !errorlevel! equ 0 (
            echo ✅ 服务启动成功
            
            REM 等待几秒钟让服务完全启动
            echo 等待服务启动...
            timeout /t 5 /nobreak >nul
            
            REM 检查服务状态
            sc query "%SERVICE_NAME%" | find "RUNNING" >nul
            if !errorlevel! equ 0 (
                echo ✅ 服务运行正常
            ) else (
                echo ⚠️  服务可能未正常启动，请检查日志
            )
        ) else (
            echo ❌ 服务启动失败
            echo 请检查日志文件: %INSTALL_DIR%\logs\agent.log
        )
    ) else (
        echo ❌ 服务创建失败
        echo.
        echo 可能的原因:
        echo   1. 权限不足
        echo   2. 服务名称冲突
        echo   3. 系统服务管理器问题
        echo.
        pause
        exit /b 1
    )
    
    echo.
    echo ========================================
    echo ✅ LightScript Agent 安装完成!
    echo ========================================
    echo.
    echo 📊 查看状态: sc query "%SERVICE_NAME%"
    echo 🛑 停止服务: net stop "%SERVICE_NAME%"
    echo 🚀 启动服务: net start "%SERVICE_NAME%"
    echo 📋 查看日志: type "%INSTALL_DIR%\logs\agent.log"
    echo 🗑️  卸载服务: "%INSTALL_DIR%\uninstall-service.bat"
    echo 📁 安装目录: %INSTALL_DIR%
    echo 🌐 管理后台: %SERVER_URL%
    echo.
    echo 💡 提示:
    echo   - Agent已作为Windows服务安装，将在系统启动时自动运行
    echo   - 首次启动可能需要几秒钟来注册Agent
    echo   - 如遇问题，请查看日志文件或联系管理员
    echo.
)

echo 🎉 安装程序执行完成!
echo.
pause